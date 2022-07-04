package radio.n2ehl;

import com.opencsv.CSVReader;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.text.WordUtils;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // command-line options, with default values
    static boolean createPdf = true;
    static boolean createDocx = false;
    static boolean keepMarkdown = false;

    public static Path generateMarkdown(String inputFilenameParm, BigDecimal startingBalanceParm, BigDecimal endingBalanceParm)  {
        inputFilename = inputFilenameParm;
        startingBalance = startingBalanceParm;
        endingBalance = endingBalanceParm;

        AtomicInteger processingRow = new AtomicInteger(-1);

        try {
            /*
            if (!processArgs(args)) {
                return;
            }
             */

            System.out.println("Parsing Quicken CSV");

            CSVReader csvReader = new CSVReader(new FileReader(inputFilename));
            List<String[]> data = csvReader.readAll();


            data.forEach(row -> {

                //noinspection ConstantConditions
                processingRow.incrementAndGet();

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
            Path markdownPath = writeMarkdown();
            return markdownPath;

            /*
            if (createPdf) {
                convertMarkdownToPdf();
            }

            if (createDocx) {
                convertMarkdownToDocx();
            }

            if (!keepMarkdown) {
                Files.delete(new File("report.md").toPath());
            }
             */
        } catch(Exception ex) {
            System.err.println("Error while processing row " + processingRow.get());
            ex.printStackTrace();
            return null;
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

    static void writeMarkdownYamlHeader(final StringBuilder sb) {
        sb.append("---\n")
        .append("author: Rich Freedman N2EHL\n")
        .append("mainfont: Consolas\n")
        .append("geometry: margin=2cm\n")
        .append("header-includes:\n")
        .append("  - |\n")
        .append("    ```{=latex}\n")
        .append("    \\usepackage[margins=raggedright]{floatrow}\n")
        .append("    ```\n")
        .append("---\n")
        ;
    }

    static Path writeMarkdown() throws Exception {
        System.out.println("Writing Intermediate Markdown file");
        StringBuilder buf = new StringBuilder();
        writeMarkdownYamlHeader(buf);

        final String reportPeriodString = getReportPeriodString();

        buf.append("# DVRA Treasurer's Report for ")
        .append(reportPeriodString)
        .append("\n")
        .append("\n")
        .append("The beginning balance for ")
        .append(getReportPeriodString())
        .append(" was $")
        .append(startingBalance)
        .append("\n\n\n")
        .append("The ending balance for ")
        .append(getReportPeriodString())
        .append(" was $")
        .append(endingBalance)
        .append(", a net ")
        .append(netTotal.compareTo(BigDecimal.ZERO) >= 0 ? "increase" : "decrease")
        .append(" of $")
        .append(netTotal.abs())
        .append("\n<p>&nbsp;</p>\n")
        .append("| **Cash Flow for ").append(reportPeriodString).append("** | | \n")
        .append("| :--------------- | --------------: |\n")
        .append("| Starting Balance | ").append(startingBalance).append("|\n")
        .append("| Ending Balance | ").append(endingBalance).append("|\n")
        .append("| | |\n")
        .append("| Total Income | ").append(totalInflows).append("|\n")
        .append("| Total Expenses | ").append(totalOutflows).append("|\n")
        .append("| | |\n")
        .append("| Net Change | ").append(netTotal).append("|\n")
        .append("\n\n<p>&nbsp;</p>\n\n");


        appendCreditCategoriesMarkdown(buf);
        buf.append("\n\n<p>&nbsp;</p>\n\n");
        appendExpenseCategoriesMarkdown(buf);
        buf.append("\n\n<p>&nbsp;</p>\n\n");
        buf.append("\n\n<p>*Respectfully Submitted by Rich Freedman N2EHL, Treasurer*</p>\n\n");

        Path tempFilePath = Files.createTempFile("temp-treasurer-report", ".md");
        Files.write(tempFilePath, buf.toString().getBytes(StandardCharsets.UTF_8));
        return tempFilePath;
    }

    static void appendCreditCategoriesMarkdown(final StringBuilder buf) {
        buf.append("**Income By Category**\n\n")
                .append("| **Category** | **Subcategory** | **Amount** | **Category Total** |\n")
                .append("| :--- | :--- | ---: | ---: |\n");

        creditCategories.forEach((categoryName, category) -> {
            buf.append("| ").append(categoryName).append(" | | | ").append(category.total).append(" |\n");

            category.subcategories.forEach(((subcategoryName, subcategory) ->
                    buf.append("| | ").append(subcategoryName).append("  | ").append(subcategory.total).append(" | |\n")
            ));
        });

        BigDecimal totalCredits = creditCategories.values()
                .stream()
                .map(Category::getTotal)
                .reduce((x, y) -> x.add(y)).get();

        buf.append("| | | | |\n");
        buf.append("| ").append("**TOTAL**").append(" | | | **").append(totalCredits).append("** |\n");
    }

    static void appendExpenseCategoriesMarkdown(final StringBuilder buf) {
        buf.append("**Expenses By Category**\n\n")
                .append("| **Category** | **Subcategory** | **Amount** | **Category Total** |\n")
                .append("| :--- | :--- | ---: | ---: |\n");

        debitCategories.forEach((categoryName, category) -> {
            buf.append("| ").append(categoryName).append(" | | | ").append(category.total).append(" |\n");

            category.subcategories.forEach(((subcategoryName, subcategory) ->
                    buf.append("| | ").append(subcategoryName).append("  | ").append(subcategory.total).append(" | |\n")
            ));

            buf.append("|||||\n|||||\n");
        });

        BigDecimal totalCredits = debitCategories.values()
                .stream()
                .map(Category::getTotal)
                .reduce((x, y) -> x.add(y)).get();

        buf.append("| | | | |\n");
        buf.append("| ").append("**TOTAL**").append(" | | | **").append(totalCredits).append("** |\n");
    }

    static void convertMarkdownToPdf() {
        System.out.println("Converting Markdown to PDF");
        try {
            Process process = new ProcessBuilder()
                    .inheritIO()
                    .command("pandoc", "--pdf-engine", "xelatex", "-s", "-o", getOutputFilenameRoot() + ".pdf", "report.md")
                    .start();
            process.waitFor();
            System.out.println("Done!");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void convertMarkdownToDocx() {
        System.out.println("Converting Markdown to Docx");
        try {
            Process process = new ProcessBuilder()
                    .inheritIO()
                    .command("pandoc", "-f", "markdown", "-t", "docx", "-o", getOutputFilenameRoot() + ".docx", "report.md")
                    .start();
            process.waitFor();
            System.out.println("Done!");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
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

    static String getOutputFilenameRoot() {
        if (transactions == null || transactions.isEmpty()) {
            return "Treasurers_Report_No_Transactions";
        }

        try {
            final LocalDate transactionDate = transactions.get(0).transactionDate;

            StringBuilder buf = new StringBuilder("DVRA_Treasurer_Report-")
                    .append(transactionDate.getYear());

            if(transactionDate.getMonthValue() < 10) {
                buf.append("0");
            }
            buf.append(transactionDate.getMonthValue());
            return buf.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "????";
        }
    }

    static String getPreviousReportPeriodString() {
        if (transactions == null || transactions.isEmpty()) {
            return "???";
        }

        try {
            final LocalDate transactionDate = transactions.get(0).transactionDate;
            return WordUtils.capitalizeFully(transactionDate.minusMonths(1).getMonth().name()) + " " + transactionDate.getYear();
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

    /*
    static boolean processArgs(final String[] args) {
        Options options = new Options();
        options.addOption(
                Option.builder("i")
                        .longOpt("input")
                        .hasArg()
                        .numberOfArgs(1)
                        .required()
                        .desc("input file - Quicken CSV export file name (required)")
                        .build()
        );

        options.addOption(
                Option.builder("s")
                        .longOpt("starting-balance")
                        .hasArg()
                        .numberOfArgs(1)
                        .required()
                        .desc("starting balance (required)")
                        .build()
        );

        options.addOption(
                Option.builder("e")
                        .longOpt("ending-balance")
                        .hasArg()
                        .numberOfArgs(1)
                        .required()
                        .desc("ending balance (required)")
                        .build()
        );

        options.addOption(
                Option.builder("pdf")
                        .longOpt("write-pdf")
                        .hasArg(false)
                        .desc("write pdf file (default: true)")
                        .build()
        );

        options.addOption(
                Option.builder("docx")
                        .longOpt("write-docx")
                        .hasArg(false)
                        .desc("write  MS Word (docx) file (default: false)")
                        .build()
        );

        options.addOption(
                Option.builder("md")
                        .longOpt("keep-md")
                        .hasArg(false)
                        .desc("preserve intermediate Markdown file (default: false)")
                        .build()
        );

        CommandLine cmd;

        try {
            CommandLineParser parser = new DefaultParser();
            cmd = parser.parse(options, args);

            if(cmd.hasOption("i")) {
                inputFilename = cmd.getOptionValue("i");
            }

            if(cmd.hasOption("s")) {
                startingBalance = new BigDecimal(cmd.getOptionValue("s"));
            }

            if(cmd.hasOption("e")) {
                endingBalance = new BigDecimal(cmd.getOptionValue("e"));
            }

            if(cmd.hasOption("pdf")) {
                createPdf = true;
            }

            if(cmd.hasOption("docx")) {
                createDocx = true;
            }

            if(cmd.hasOption("md")) {
                keepMarkdown = true;
            }

        } catch(Exception ex) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "quicken-treasurer-report <options>", options );
            return false;
        }

        System.out.printf("input file: %s\n", inputFilename);
        System.out.println("startingBalance " + startingBalance);
        System.out.println("endingBalance " + endingBalance);
        System.out.println("createPdf " + createPdf);
        System.out.println("createDocx " + createDocx);
        System.out.println("keepMarkdown " + keepMarkdown);

        return true;
    }
     */

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
