module treasurers_report {
    requires javafx.controls;
    requires org.apache.logging.log4j;
    requires com.opencsv;
    requires lombok;
    requires org.apache.commons.text;
    requires java.desktop;
    requires commons.exec;
    requires org.apache.commons.lang3;
    requires flexmark;
    requires flexmark.ext.toc;
    requires flexmark.pdf.converter;
    requires flexmark.profile.pegdown;
    requires flexmark.util.ast;
    requires flexmark.util.data;
    requires org.apache.commons.io;
    requires flexmark.util.misc;

    exports radio.n2ehl;
    exports radio.n2ehl.skin;
}
