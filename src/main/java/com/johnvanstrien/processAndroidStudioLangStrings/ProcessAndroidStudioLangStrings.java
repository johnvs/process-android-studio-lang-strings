package com.johnvanstrien.processAndroidStudioLangStrings;

import org.apache.commons.cli.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProcessAndroidStudioLangStrings {
    // java -jar <jar-file-name>.jar
    // Todo
    //  1. Improve parsing of multi-line comments
    //  2. Refactor Export method
    //  3. Add Import (and maybe export) Report log file
    //  4. Convert the whole project to Kotlin

    static final String STRINGS = "strings";
    static final String OLD = "OLD";
    static final String FILE_EXTENSION_XML = "xml";
    static final String FILE_EXTENSION_CSV = "csv";
    static final String LANG_CODE_SEPARATOR = "-";
    static final String STRING_TAG_START = "<string";
    static final String STRING_TAG_START_WITH_NAME = "    <string name=\"";
    static final String STRING_TAG_START_CLOSE = "\">";
    static final String STRING_TAG_END = "</string>";
    static final String STRING_VALUE_START = ">";
    static final String TRANSLATABLE_ATTR = "translatable";
    static final String DOUBLE_QUOTE = "\"";
    static final String LINE_FEED = "\n";
    static final String COMMENT_START = "<!--";
    static final String COMMENT_END = "-->";
    static final String OUTPUT_FILE_PREFIX = "ToBeTranslated-";
    static final String STRING_NAME = "String Name";
    static final String INPUT_FILE_PREFIX = "translated-";
    static final String REGEX_LEADING_WHITESPACE = "^\\s*";

    // Command line options
    static final String CMD_LINE_OPTION_EXPORT = "e";
    static final String CMD_LINE_OPTION_LONG_EXPORT = "export";
    static final String CMD_LINE_OPTION_IMPORT = "i";
    static final String CMD_LINE_OPTION_LONG_IMPORT = "import";
    static final String CMD_LINE_OPTION_FILE_FORMAT = "ff";
    static final String CMD_LINE_OPTION_LONG_FILE_FORMAT = "fileformat";
    static final String FILE_FORMAT_CSV = "csv";
    static final String FILE_FORMAT_EXCEL = "excel";

    static final String ENGLISH_CODE = "en";
    static final String SPANISH_CODE = "es";
    static final String CHINESE_CODE = "zh";
    static final String defaultLanguageCode = ENGLISH_CODE;

    static final String ENGLISH_TITLE = "English";
    static final String SPANISH_TITLE = "Spanish";
    static final String CHINESE_TITLE = "Chinese";

    static final Map<String, String> languageNames;
    static {
        Map<String, String> langNames = new HashMap<>();
        langNames.put(ENGLISH_CODE, ENGLISH_TITLE);
        langNames.put(SPANISH_CODE, SPANISH_TITLE);
        langNames.put(CHINESE_CODE, CHINESE_TITLE);
        languageNames = Collections.unmodifiableMap(langNames);
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();

        // Initialize command line objects
        Option optionExport = Option.builder(CMD_LINE_OPTION_EXPORT)
                .longOpt(CMD_LINE_OPTION_LONG_EXPORT)
                .hasArg(false)
                .desc("Export strings needing translation")
                .build();

        Option optionImport = Option.builder(CMD_LINE_OPTION_IMPORT)
                .longOpt(CMD_LINE_OPTION_LONG_IMPORT)
                .hasArg(false)
                .desc("Import translated strings")
                .build();

        OptionGroup optionGroupExportImport = new OptionGroup();
        optionGroupExportImport
                .addOption(optionExport)
                .addOption(optionImport)
                .setRequired(true);

        Option optionFileFormat = Option.builder(CMD_LINE_OPTION_FILE_FORMAT)
                .longOpt(CMD_LINE_OPTION_LONG_FILE_FORMAT)
                .numberOfArgs(1)
                .optionalArg(false)
                .desc("File format is either CSV or Excel XLSX")
                .build();

        Options options = new Options();
        options.addOptionGroup(optionGroupExportImport);
        options.addOption(optionFileFormat);

        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("Error encountered trying to parse the command options - " + e);

            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "processAndroidStudioLangStrings", options );
        }

//      Todo    1. Reconfigure export/input options
//              2. add file format command line options
//              3. add functionality to read from and write to Excel files

        if (commandLine != null) {
            if (commandLine.hasOption(CMD_LINE_OPTION_EXPORT)) {
//                if (commandLine has fileformat options) {
                Export.process();
            }
            if (commandLine.hasOption(CMD_LINE_OPTION_IMPORT)) {
                Import.process();
            }
        }
    }
}
