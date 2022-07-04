package radio.n2ehl;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;

public class TreasurersReport extends Application {


    final Label label = new Label("no CSV file selected");
    final Button btnSelectInputFile = new Button("Open CSV file...");
    final Button btnSaveButton = new Button("Generate Report...");
    final MoneyField fldStartBal = new MoneyField();
    final MoneyField fldEndBal = new MoneyField();

    final String moneyRegex = "^[+-]?[0-9]{1,3}(?:,?[0-9]{3})*\\.[0-9]{2}$";

    File inputCSVFile;
    FileChooser fileChooser;
    Stage stage;
    File outputPdfFile;

    @Override
    public void start(Stage stage) {
        setupScene(stage);
    }

    private void setupScene(Stage stage) {
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
                            generateReport();
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

    private void generateReport() {
        LogManager.getLogger().info("*** GENERATE REPORT");
        LogManager.getLogger().info("startBal = " + fldStartBal.getValue());
        LogManager.getLogger().info("endBal = " + fldEndBal.getValue());
        LogManager.getLogger().info("csv = " + inputCSVFile);
        LogManager.getLogger().info("output = " + outputPdfFile);

        Path markdownPath = generateMarkdownFile(inputCSVFile.getAbsolutePath(), fldStartBal.getValue(), fldEndBal.getValue());
        if(markdownPath != null) {
            convertMarkdownToPDF(markdownPath);
        }
    }


    private Path generateMarkdownFile(String inputFileName, BigDecimal startingBalance, BigDecimal endingBalance) {
        // generates the markdown in a temp file
        return ReportGenerator.generateMarkdown(inputFileName, startingBalance, endingBalance);
    }

    private void convertMarkdownToPDF(Path markdownPath) {
        LogManager.getLogger().info("would convert file: " + markdownPath + " to pdf");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
