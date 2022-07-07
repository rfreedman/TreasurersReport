package radio.n2ehl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.pdf.converter.PdfConverterExtension;
import com.vladsch.flexmark.profile.pegdown.Extensions;
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;

import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

import com.vladsch.flexmark.parser.PegdownExtensions;


public class MarkdownToPdfConverter {


    static final DataHolder OPTIONS = PegdownOptionsAdapter.flexmarkOptions(
                 Extensions.ALL & ~(Extensions.ANCHORLINKS | Extensions.EXTANCHORLINKS_WRAP
      ), new Extension[]{TocExtension.create()}).toMutable()

            .set(TocExtension.LIST_CLASS, PdfConverterExtension.DEFAULT_TOC_LIST_CLASS)
            .toImmutable();

    public static boolean convertMarkdownToPdf(final Path markdownPath, final Path pdfPath) throws IOException {
        final String markdown = readMarkdown(markdownPath);
        final Parser markdownParser = Parser.builder(OPTIONS).build();
        final Node document = markdownParser.parse(markdown);

        final HtmlRenderer htmlRenderer = HtmlRenderer.builder(OPTIONS).build();
        final String html = htmlRenderer.render(document);

        final String css =
                """
                            * {
                               font-family: Consolas, sans-serif, sans-serif !important;
                            }
                    
                            html {
                                padding-left: 60px;
                                padding-right: 60px;
                            }
                    
                            p {
                                font-size: 0.8em;
                                font-weight: 200;
                            }
                            
                            h1 {
                                font-size: 1.3em;
                            }
                    
                            table {
                               border-spacing: 0 !important;
                               border-bottom: 2px solid black;
                               font-size: 0.8em;
                               font-weight: 300;
                            }
                    
                            table thead tr th  {
                                border-top: 2px solid #000000 !important;
                                border-bottom: 2px solid #000000 !important;
                                padding-left: 20px !important;
                                white-space: nowrap; 
                            }
                    
                            table thead tr th:first-child {
                                padding-left: 0 !important;
                            }
                    
                            table tbody tr td  {
                                padding-left: 20px !important;   
                                white-space: nowrap;                   
                            }
                            
                            table tbody tr td:first-child  {
                                padding-left: 0 !important;
                            }
                            
                            td:empty:after {
                                content: "\00a0";
                            }
                            """;



        String htmlWithCss = PdfConverterExtension.embedCss(html, css);
        PdfConverterExtension.exportToPdf(pdfPath.toAbsolutePath().toString(), htmlWithCss, "", OPTIONS);


        return true;
    }

    private static String readMarkdown(final Path markdownPath) throws IOException {
        return Files.readString(markdownPath);
    }
}
