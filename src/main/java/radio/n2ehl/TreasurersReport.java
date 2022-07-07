package radio.n2ehl;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class TreasurersReport extends Application {

    final Label label = new Label("no CSV file selected");
    final Button btnSelectInputFile = new Button("Open CSV file...");
    final Button btnSaveButton = new Button("Generate Report...");
    final MoneyField fldStartBal = new MoneyField();
    final MoneyField fldEndBal = new MoneyField();

    File inputCSVFile;
    FileChooser fileChooser;
    Stage stage;
    File outputPdfFile;

    String pandocPath = "";

    @Override
    public void start(Stage stage) {
        setupScene(stage);
    }

    private void setupScene(Stage stage) {
        MenuBar menuBar = new MenuBar();

        // --- Menu File
        Menu menuFile = new Menu("File");

        // --- Menu Edit
        Menu menuEdit = new Menu("Edit");

        // --- Menu View
        Menu menuView = new Menu("View");

        menuBar.getMenus().addAll(menuFile, menuEdit, menuView);

        final String os = System.getProperty("os.name");
        if (os != null && os.startsWith("Mac")) {
            Platform.runLater(() -> menuBar.setUseSystemMenuBar(true));
        }


        this.stage = stage;
        this.stage.setTitle("Treasurer's Report Generator");
        this.fileChooser = buildFileChooser();

        btnSelectInputFile.setOnAction(getInputFileEventHandler());
        btnSaveButton.setOnAction(getOutputFileEventHandler());

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.TOP_CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        int rowIndex = 0;

        Text sceneTitle = new Text("Treasurer's Report Generator");
        sceneTitle.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(sceneTitle, 0, rowIndex++, 2, 1);

        Text instructions = new Text("""
                Instructions:
                1. Export the month's transactions from Quicken as a CSV file
                2. Enter the month's starting and ending balance
                3. Select the exported CSV file via the 'Open CSV' button
                4. Generate and save the report via the 'Generate Report' button"""
        );
        rowIndex += 2;        grid.add(instructions, 0, rowIndex++, 2, 1);

        rowIndex++;
        Label lblStartBal = new Label("Starting Bal.");
        grid.add(lblStartBal, 0, rowIndex);
        grid.add(fldStartBal, 1, rowIndex++);

        Label lblEndBal = new Label("Ending Bal.");
        grid.add(lblEndBal, 0, rowIndex);
        grid.add(fldEndBal, 1, rowIndex++);

        rowIndex +=4;
        GridPane.setHalignment(btnSelectInputFile, HPos.CENTER);
        grid.add(btnSelectInputFile, 0, rowIndex++, 2, 1);

        GridPane.setHalignment(label, HPos.CENTER);
        grid.add(label, 0, 5, 2, rowIndex++);

        rowIndex += 10;
        GridPane.setHalignment(btnSaveButton, HPos.CENTER);
        grid.add(btnSaveButton, 0, 7, 2, rowIndex++);
        btnSaveButton.setDisable(true);

        Scene scene = new Scene(grid, 600, 450);
        stage.setScene(scene);
        stage.show();

        /*
        try {
            if (!checkPandocInstallation()) {
                showError("Pandoc is required to produce the report,\n but was not found on path or at any other known location");
                System.exit(-1);
            }
        } catch (IOException ex) {
            showError("IOException while attempting to check for pandoc installation");
            System.exit(-1);

        }
         */
    }

    private void showError(String errorString) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage.getScene().getWindow());
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setHeaderText(null);
        alert.setContentText(errorString);
        alert.showAndWait();
    }

    /*
    private boolean panDocInstalled() {
        showError(System.getenv().get("PATH"));

        // ProcessBuilder builder = new ProcessBuilder("/usr/local/bin/pandoc", "-v").inheritIO();
        ProcessBuilder builder = new ProcessBuilder("pandoc", "-v").inheritIO();

        try {
            final Process process = builder.start();
            boolean result = process.waitFor(10, TimeUnit.SECONDS);
            if(!result) {
                return false; // timed out
            }
            int exitValue = process.exitValue();
            return exitValue == 0;
        } catch (IOException | InterruptedException ex) {
            LogManager.getLogger().error("failed to exec pandoc", ex);
            return false;
        }
    }
     */

    private boolean checkPandocInstallation() throws IOException {
        /*
        // on the PATH
        if(tryPandocOnPath("pandoc")) {
            pandocPath = "pandoc";
            return true;
        }
         */

        // /usr/local/bin is a candidate, but not in the environment's PATH
        if (tryPandocOnPath("/usr/local/bin/pandoc")) {
            pandocPath = "/usr/local/bin/pandoc";
            return true;
        }

        return false;
    }

    // path must either be blank (to use the system path)
    // or end in a slash (to find pandoc at that path)
    private boolean tryPandocOnPath(String path) throws IOException {

        /*
        ProcessBuilder builder = new ProcessBuilder(path, "-v").inheritIO();

        try {
            final Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());

            boolean result = process.waitFor(10, TimeUnit.SECONDS);

            showError("execution of " + pandocPath + " got exit value " + process.exitValue());
            showError("Output:\n" + output);

            return process.exitValue() == 0;

        } catch (IOException  | InterruptedException ex) {
            LogManager.getLogger().error("failed to exec pandoc", ex);
            return false;
        }
         */

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(stdout);
        CommandLine cl = CommandLine.parse(path + " -v");
        DefaultExecutor exec = new DefaultExecutor();
        exec.setStreamHandler(psh);
        int exitCode = exec.execute(cl);
        showError(stdout.toString());
        return exitCode == 0;
    }




    void onChange() {
        if(fldStartBal.getValue() != null &&
           fldEndBal.getValue() != null &&
           inputCSVFile != null
        ) {
            btnSaveButton.setDisable(false);
        } else {
            btnSaveButton.setDisable(true);
        }
    }


    private EventHandler getInputFileEventHandler() {
        final EventHandler<ActionEvent> eventHandler =
                new EventHandler<>() {
                    public void handle(ActionEvent e) {
                        File file = fileChooser.showOpenDialog(stage);
                        setInputCSVFile(file);
                        onChange();
                    }
                };
        return eventHandler;
    }

    private EventHandler getOutputFileEventHandler() {
        final EventHandler<ActionEvent> eventHandler =
                new EventHandler<>() {
                    public void handle(ActionEvent e) {

                        final FileChooser outputFileChooser = new FileChooser();
                        outputFileChooser.setTitle("Select Quicken CSV export file");
                        outputFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
                        File outputFile = outputFileChooser.showSaveDialog(stage);

                        if(outputFile != null) {
                            outputPdfFile = outputFile;
                            generateReport(outputFile.getAbsolutePath());
                        }


                    }
                };
        return eventHandler;
    }

    private void setInputCSVFile(File file) {
        inputCSVFile = file;
        label.setText(file.getName() + "  selected");
    }


    private FileChooser buildFileChooser() {
        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Quicken CSV export file");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        return fileChooser;
    }

    private void generateReport(final String outputPath) {
        LogManager.getLogger().info("*** GENERATE REPORT");
        LogManager.getLogger().info("startBal = " + fldStartBal.getValue());
        LogManager.getLogger().info("endBal = " + fldEndBal.getValue());
        LogManager.getLogger().info("csv = " + inputCSVFile);
        LogManager.getLogger().info("output = " + outputPdfFile);

        Path markdownPath = generateMarkdownFile(inputCSVFile.getAbsolutePath(), fldStartBal.getValue(), fldEndBal.getValue());
        if(markdownPath != null) {
            try {
                if(convertMarkdownToPDF(markdownPath.toFile(), new File(outputPath))) {
                    java.awt.Desktop.getDesktop().open(outputPdfFile);
                } else {
                    showError("Got non-zero return on pdf conversion");
                }
            } catch(IOException ex) {
                showError("Encountered an error while converting markdown file to PDF:\n" + ExceptionUtils.getStackTrace(ex));
            }
        }
    }


    private Path generateMarkdownFile(String inputFileName, BigDecimal startingBalance, BigDecimal endingBalance) {
        // generates the markdown in a temp file
        return ReportGenerator.generateMarkdown(inputFileName, startingBalance, endingBalance);
    }

    private boolean convertMarkdownToPDF(File markdownFile, File outputPdfFile) throws IOException {
        /*
        try {
            Process process = new ProcessBuilder()
                    .inheritIO()
                    .command(pandocPath, "--pdf-engine", "xelatex", "-s", "-o", outputPdfFile.getAbsolutePath(), markdownFile.getAbsolutePath())
                    .start();
            List<String> results = readOutput(process.getInputStream());

            process.waitFor();
            if(process.exitValue() == 0) {
                showError("it worked?");
                return true;
            } else {
                showError("execution of " + pandocPath + " got exit value " + process.exitValue());

                StringBuilder sb = new StringBuilder();
                String sep = "\n";
                for(String s: results) {
                    sb.append(sep).append(s);
                }
                showError(sb.toString());

            }
        } catch (IOException | InterruptedException e) {
            // LogManager.getLogger().error("failed to convert markdown to pdf", e);
            showError("failed to convert to pdf " + e.getMessage());
            return false;
        }

        return true;
         */

        /*
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(stdout);
        CommandLine cl = CommandLine.parse(pandocPath + " --pdf-engine=xelatex --verbose --log=/Users/rfreedman/pandoc.log  -s  --output=" +  outputPdfFile.getAbsolutePath() + " " +  markdownFile.getAbsolutePath());
        showError(cl.toString());
        DefaultExecutor exec = new DefaultExecutor();
        exec.setExitValue(0);
        exec.setStreamHandler(psh);
        int exitCode = exec.execute(cl);
        showError(stdout.toString());
        return exitCode == 0;
         */


        MarkdownToPdfConverter.convertMarkdownToPdf(markdownFile.toPath(), outputPdfFile.toPath());
        return true;
    }

    private List<String> readOutput(InputStream inputStream) throws IOException {
        List<String> results = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        while(reader.ready()) {
            results.add(reader.readLine());
        }
        return results;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
