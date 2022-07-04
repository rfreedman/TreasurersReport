

A JavaFX application that Generates a "Treasurer's Report" from a CSV file of transactions exported from Quicken


### Usage
**Running with gradle:**
```
./gradlew run
```

* In Quicken, reconcile all accounts for the previous month
* From Quicken, export all transactions for the previous month to a CSV file
* Run the application
* Enter the previous month's starting and ending balances
* Select the CSV file that you exported from Quicken
* Click the "Generate Report" button to specify where to save the PDF file, and give it a name
* Done!g



**Creating and executing a custom runtime image:**
```
./gradlew jlink
cd build/image/bin
./helloFX
```

**Creating installable packages**
```
./gradlew jpackage
```

The above command will generate the platform-specific installers in the `build/jpackage` directory.

:bulb: You can check the artifacts produced by the [GitHub actions used to build this project](https://github.com/beryx-gist/badass-jlink-example-log4j2-javafx/actions?query=workflow%3A%22Gradle+Build%22) and download an application package for your platform (such as [from here](https://github.com/beryx-gist/badass-jlink-example-log4j2-javafx/actions/runs/1198565779#artifacts)).
