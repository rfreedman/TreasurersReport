package radio.n2ehl;

import com.opencsv.CSVReader;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.text.WordUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportGenerator {
    static final int COL_DATE = 3;
    static final int COL_PAYEE = 5;
    static final int COL_CATEGORY = 6;
    static final int COL_AMOUNT = 9;
    static final int COL_ACCOUNT = 10;
    static final int COL_NOTES = 11;


    static String inputFilename = null;
    static BigDecimal startingBalance = null;
    static BigDecimal endingBalance = null;
    static BigDecimal totalInflows = null;
    static BigDecimal totalOutflows = null;
    static BigDecimal netTotal = null;

    static List<Transaction> transactions = new ArrayList<>();
    static Map<String, Category> creditCategories = new HashMap<>();
    static Map<String, Category> debitCategories = new HashMap<>();

    static String submittedLine = null;

    public static Path generateMarkdown(String inputFilenameParm, BigDecimal startingBalanceParm, BigDecimal endingBalanceParm)  {
        inputFilename = inputFilenameParm;
        startingBalance = startingBalanceParm;
        endingBalance = endingBalanceParm;

        readConfig();

        AtomicInteger processingRow = new AtomicInteger(-1);

        try {
              System.out.println("Parsing Quicken CSV");

            CSVReader csvReader = new CSVReader(new FileReader(inputFilename));
            List<String[]> data = csvReader.readAll();


            data.forEach(row -> {

                processingRow.incrementAndGet();

                //noinspection ConstantConditions
                do {
                    if(row.length > COL_CATEGORY &&  row[COL_CATEGORY].contains("Transfer:")) {
                        break; // ignore transfers
                    }

                    if (row[0].contains("Total Inflows:")) {
                        // totalInflows = new BigDecimal(row[COL_AMOUNT].trim());
                        break;
                    }

                    if (row[0].contains("Total Outflows:")) {
                        // totalOutflows = new BigDecimal(row[COL_AMOUNT].trim());
                        break;
                    }

                    if (row[0].contains("Net Total:")) {
                        //  netTotal = new BigDecimal(row[1].trim());
                        break;
                    }

                    if (row.length > COL_DATE && isDate(row[COL_DATE])) {
                        String[] categoryParts = row[COL_CATEGORY].split(":");

                        Transaction transaction = Transaction.builder()
                                .transactionDate(parseDate(row[COL_DATE].trim()))
                                .payee(row[COL_PAYEE].trim())
                                .category(categoryParts[0])
                                .subCategory(categoryParts.length > 1 ? categoryParts[1] : "Other")
                                .amount(new BigDecimal(row[COL_AMOUNT]))
                                .account(row[COL_ACCOUNT])
                                .notes(row[COL_NOTES].trim())
                                .build();

                        transactions.add(transaction);
                        break;
                    }
                } while (false);
            });

            createCategories();
            calculateTotals();
            return writeMarkdown();

           } catch(Exception ex) {
            System.err.println("Error while processing row " + processingRow.get());
            ex.printStackTrace();
            return null;
        }
    }

    static void readConfig() {
        final String userHome = System.getProperty("user.home");
        if(userHome != null) {
            try {
                final List<String> configLines = Files.readAllLines(Path.of(userHome, ".treasurer-report"));
                for(String line : configLines) {
                    if(line.trim().startsWith("#")) {
                        continue;
                    }
                    if(line.trim().startsWith("submitted_line")) {
                        final String[] pieces = line.trim().split("=");
                        if(pieces.length == 2) {
                            submittedLine = pieces[1];
                        }
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

    }

    static boolean isDate(final String str) {
        if(str.length() < 8 || str.length() > 10) {
            return false;
        }

        final String[] dateParts = str.split("/");
        if(dateParts.length != 3) {
            return false;
        }

        if(dateParts[0].length() < 1 || dateParts[0].length() > 2) {
            return false;
        }

        if(dateParts[1].length() < 1 || dateParts[1].length() > 2) {
            return false;
        }

        return dateParts[2].length() == 4;
    }

    static void createCategories() {
        System.out.println("Categorizing Transactions");
        transactions.stream().filter(transaction -> transaction.amount.compareTo(BigDecimal.ZERO) >= 0).forEach(deposit -> categorizeTransaction(creditCategories, deposit));
        transactions.stream().filter(transaction -> transaction.amount.compareTo(BigDecimal.ZERO) < 0).forEach(payment -> categorizeTransaction(debitCategories, payment));
    }

    static void categorizeTransaction(final Map<String, Category> categoryMap, final Transaction transaction) {
        if (!categoryMap.containsKey(transaction.category)) {
            Category category = new Category(transaction.category, BigDecimal.ZERO, new HashMap<>());
            categoryMap.put(transaction.category, category);
        }
        final Category category = categoryMap.get(transaction.category);
        Subcategory subcategory = category.subcategories.get(transaction.subCategory);
        if (subcategory == null) {
            subcategory = new Subcategory(transaction.subCategory, BigDecimal.ZERO, new ArrayList<>());
            category.subcategories.put(subcategory.name, subcategory);
        }
        subcategory.transactions.add(transaction);
        subcategory.total = subcategory.total.add(transaction.amount);
        category.total = category.total.add(transaction.amount);
    }

    static void calculateTotals() {
        totalInflows = creditCategories.values().stream().map(Category::getTotal).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
        totalOutflows = debitCategories.values().stream().map(Category::getTotal).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
        netTotal = totalInflows.add(totalOutflows);
    }

    static Path writeMarkdown() throws Exception {
        final String reportPeriodString = getReportPeriodString();

        StringBuilder buf = new StringBuilder();

        buf.append("# DVRA Treasurer's Report for ")
        .append(reportPeriodString)
        .append("\n")
        .append("\n")
        .append("<p>The beginning balance for ")
        .append(getReportPeriodString())
        .append(" was ")
        .append(NumberFormat.getCurrencyInstance().format(startingBalance))
        .append("\n\n\n")
        .append("The ending balance for ")
        .append(getReportPeriodString())
        .append(" was ")
        .append(NumberFormat.getCurrencyInstance().format(endingBalance)) //endingBalance.setScale(2).toPlainString())
        .append(", a net ")
        .append(netTotal.compareTo(BigDecimal.ZERO) >= 0 ? "increase" : "decrease")
        .append(" of ")
        .append(NumberFormat.getCurrencyInstance().format(netTotal.abs()))
        .append("</p>\n\n")
        .append("<p><br/></p>\n\n")
        .append("| **Cash Flow for ").append(reportPeriodString).append("** || \n")
        .append("| :--- | ---: |\n")
        .append("| Starting Balance | ").append(NumberFormat.getCurrencyInstance().format(startingBalance)).append("|\n")
        .append("| Ending Balance | ").append(NumberFormat.getCurrencyInstance().format(endingBalance)).append("|\n")
        .append("| <br/> | <br/> |\n")
        .append("| Total Income | ").append(totalInflows).append("|\n")
        .append("| Total Expenses | ").append(totalOutflows).append("|\n")
        .append("| <br/> | <br/> |\n")
        .append("| Net Change | ").append(netTotal).append("|\n")
        .append("\n\n<p></p>\n\n");

        appendCreditCategoriesMarkdown(buf);

        buf.append("\n\n<p></p>\n\n");
        appendExpenseCategoriesMarkdown(buf);
        buf.append("\n\n<p></p>\n\n");
        // buf.append("\n\n<p><i>Respectfully Submitted by Rich Freedman N2EHL, Treasurer</i></p>\n\n");
        if(submittedLine != null) {
            buf.append("\n\n<p><i>" + submittedLine + "</i></p>\n\n");
        }

        FileAttribute<Set<PosixFilePermission>> rwx = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"));
        Path tempFilePath = Files.createTempFile("temp-treasurer-report", ".md", rwx);
        Files.writeString(tempFilePath, buf.toString());
        return tempFilePath;
    }

    static void appendCreditCategoriesMarkdown(final StringBuilder buf) {
        buf.append("<br/><br/>**Income By Category**\n\n")
                .append("| **Category** | **Subcategory** | **Amount** | **Category Total** |\n")
                .append("| :--- | :--- | ---: | ---: |\n");

        creditCategories.forEach((categoryName, category) -> {
            buf.append("| ").append(categoryName).append(" |  |  |  ").append(category.total).append(" |\n");

            category.subcategories.forEach(((subcategoryName, subcategory) ->
                    buf.append("|  | ").append(subcategoryName).append("  | ").append(subcategory.total).append(" |  |\n")
            ));
        });

        BigDecimal totalCredits = creditCategories.values()
                .stream()
                .map(Category::getTotal)
                .reduce(BigDecimal::add).orElse(BigDecimal.ZERO);

        buf.append("|||||\n");
        buf.append("|").append("**TOTAL**").append("||| **").append(totalCredits).append("** |\n");
    }

    static void appendExpenseCategoriesMarkdown(final StringBuilder buf) {
        buf.append("<br/><br/>**Expenses By Category**\n\n")
                .append("| **Category** | **Subcategory** | **Amount** | **Category Total** |\n")
                .append("| :--- | :--- | ---: | ---: |\n");

        debitCategories.forEach((categoryName, category) -> {
            buf.append("| ").append(categoryName).append(" |  |  | ").append(category.total).append(" |\n");

            category.subcategories.forEach(((subcategoryName, subcategory) ->
                    buf.append("|  | ").append(subcategoryName).append("  | ").append(subcategory.total).append(" | |\n")
            ));

            buf.append("|||||\n|||||\n");
        });

        BigDecimal totalCredits = debitCategories.values()
                .stream()
                .map(Category::getTotal)
                .reduce(BigDecimal::add).orElse(BigDecimal.ZERO);

        buf.append("|||||\n");
        buf.append("|||||\n");
        buf.append("|").append("**TOTAL**").append(" ||| **").append(totalCredits).append("**|\n");
    }

    static String getReportPeriodString() {
        if (transactions == null || transactions.isEmpty()) {
            return "???";
        }

        try {
            LocalDate transactionDate = transactions.get(0).transactionDate;
            return WordUtils.capitalizeFully(transactionDate.getMonth().name()) + " " + transactionDate.getYear();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "????";
        }
    }

    static LocalDate parseDate(final String str) {
        final String[] dateParts = str.split("/");
        return LocalDate.of(
                Integer.parseInt(dateParts[2]), // incoming format is month/day/year
                Integer.parseInt(dateParts[0]),
                Integer.parseInt(dateParts[1])
        );
    }

    @Data
    @Builder
    static class Category {
        public String name;
        public BigDecimal total;
        public Map<String, Subcategory> subcategories;
    }

    @Data
    @Builder
    static class Subcategory {
        public String name;
        public BigDecimal total;
        public List<Transaction> transactions;
    }

    @Data
    @Builder
    static class Transaction {
        public LocalDate transactionDate;
        public String payee;
        public String category;
        public String subCategory;
        public BigDecimal amount;
        public String account;
        public String notes;
    }
}
