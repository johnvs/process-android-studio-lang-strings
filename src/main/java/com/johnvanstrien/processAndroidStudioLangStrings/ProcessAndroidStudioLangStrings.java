package com.johnvanstrien.processAndroidStudioLangStrings;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public class ProcessAndroidStudioLangStrings {

    // java -jar <jar-file-name>.jar
    // Todo
    //  1. Improve parsing of multi-line comments
    //  2. Split up Export method
    //

    private static final String STRINGS = "strings";
    private static final String OLD = "OLD";
    private static final String FILE_EXTENSION_XML = "xml";
    private static final String FILE_EXTENSION_CSV = "csv";
    private static final String LANG_CODE_SEPARATOR = "-";
    private static final String STRING_TAG_START = "<string";
    private static final String STRING_TAG_START_WITH_NAME = "    <string name=\"";
    private static final String STRING_TAG_START_CLOSE = "\">";
    private static final String STRING_TAG_END = "</string>";
    private static final String STRING_VALUE_START = ">";
    private static final String TRANSLATABLE_ATTR = "translatable";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String LINE_FEED = "\n";
    private static final String COMMENT_START = "<!--";
    private static final String COMMENT_END = "-->";
    private static final String OUTPUT_FILE_PREFIX = "ToBeTranslated-";
    private static final String INPUT_FILE_PREFIX = "translated-";
    private static final String REGEX_LEADING_WHITESPACE = "^\\s*";

    // Command line options
    private static final String CMD_LINE_OPTION_EXPORT = "e";
    private static final String CMD_LINE_OPTION_LONG_EXPORT = "export";
    private static final String CMD_LINE_OPTION_IMPORT = "i";
    private static final String CMD_LINE_OPTION_LONG_IMPORT = "import";

    private Map<String, String> defaultLangStrings;
    private Map<String, Set<String>> foreignLangStringNameSets;
    private Map<String, Map<String, String>> toBeTranslatedStringSets;

    private static final String ENGLISH_CODE = "en";
    private static final String SPANISH_CODE = "es";
    private static final String CHINESE_CODE = "zh";
    private String defaultLanguageCode = ENGLISH_CODE;

    private static final String ENGLISH_TITLE = "English";
    private static final String SPANISH_TITLE = "Spanish";
    private static final String CHINESE_TITLE = "Chinese";

    private static final Map<String, String> languageNames;
    static {
        Map<String, String> langNames = new HashMap<>();
        langNames.put(ENGLISH_CODE, ENGLISH_TITLE);
        langNames.put(SPANISH_CODE, SPANISH_TITLE);
        langNames.put(CHINESE_CODE, CHINESE_TITLE);

        languageNames = Collections.unmodifiableMap(langNames);
    }

    // Used to read data from CSV files
    private enum InputColumns {
        stringName,
        english,
        foreignLang;
    }

    // Find all the files with the given extension and return them as a List<File>
    private List<File> getFileNamesWithExtAsList(String fileExt) {
        File[] filesList = new File(".").listFiles();
        List<File> fileList = new ArrayList<>();

        if (filesList != null) {
            for (File stringFile : filesList) {
                if (stringFile.isFile() && stringFile.getName().endsWith(fileExt)) {
                    fileList.add(stringFile);
                    System.out.println(stringFile.getName());
                }
            }
        }
        return fileList;
    }

    private File getFileWithLangCodeAndExt(String langCode, String fileExt) {
        File[] filesList = new File(".").listFiles();
        File result = null;

        if (filesList != null) {
            for (File file : filesList) {
                if (file.isFile() && langCode.equals(getLanguageCode(file)) && file.getName().endsWith(fileExt)) {
                    result = file;
                    System.out.println(file.getName());
                }
            }
        }
        return result;
    }

    private String getLanguageCode(File file) {
        String fileName = file.getName();
        int languageCodeStart = fileName.indexOf(LANG_CODE_SEPARATOR) + 1;
        int languageCodeEnd = languageCodeStart + 2;
//        String langCode = fileName.substring(languageCodeStart, languageCodeEnd);
        return fileName.substring(languageCodeStart, languageCodeEnd);
    }

    private Map<String, List<String>> getLinesFromFiles(List<File> files) {

        Map<String, List<String>> fileLineMap = new HashMap<>();

        for (File stringFile : files) {
            String languageCode = getLanguageCode(stringFile);

            try {
                fileLineMap.put(languageCode, Files.readAllLines(stringFile.toPath()));
            } catch (IOException e) {
                System.out.println(String.format("Error reading lines from file %s: %s", stringFile.getName(), e));
            }
        }

        return fileLineMap;
    }

    private Map<String, String> getStringsFromXmlFile(List<String> fileByLine) {

        Map<String, String> stringSet = new HashMap<>();  // Map<string name, string value>

        String stringName = "";
        StringBuilder stringValueSb = null;
        boolean isLineContinuation = false;

        for (String line : fileByLine) {

            String tempLine = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");

            if (!isLineContinuation) {
                if (!tempLine.startsWith(COMMENT_START) && line.contains(STRING_TAG_START) && !line.contains(TRANSLATABLE_ATTR)) {
                    // Code currently assumes that any "translatable" attributes are set to false
                    // We are on a line with at least the start of a string that needs translating
                    // <string name=“string1”>This is a simple string.</string>
                    int stringNameStartIndex = line.indexOf(DOUBLE_QUOTE) + 1;
                    int stringNameEndIndex = line.indexOf(DOUBLE_QUOTE, stringNameStartIndex);
                    stringName = line.substring(stringNameStartIndex, stringNameEndIndex);

                    if (line.contains(STRING_TAG_END)) {
                        // The whole string is on one line
                        stringValueSb = new StringBuilder(line.substring(line.indexOf(STRING_VALUE_START) + 1, line.indexOf(STRING_TAG_END)));
                        stringSet.put(stringName, stringValueSb.toString());
                    } else {
                        // The string continues on the next line (hopefully)
                        stringValueSb = new StringBuilder(line.substring(line.indexOf(STRING_VALUE_START) + 1));
                        stringValueSb.append(LINE_FEED);
                        isLineContinuation = true;
                    }
                }
            } else {
                // This line is part of a multi line string
                if (line.contains(STRING_TAG_END)) {
                    // and this is the last line of it
                    stringValueSb.append(line, 0, line.indexOf(STRING_TAG_END));
                    stringSet.put(stringName, stringValueSb.toString());
                    isLineContinuation = false;
                } else {
                    // and this is not the last line of it
                    stringValueSb.append(line);
                    stringValueSb.append(LINE_FEED);
                }
            }
        }
        return stringSet;
    }

    private Map<String, String> getStringsFromCsvFile(File file) {

        Map<String, String> stringSet = new HashMap<>();
        try {
            Reader fileReader = new FileReader(file);
            try {
                CSVParser records = new CSVParser(fileReader, CSVFormat.EXCEL.withHeader(InputColumns.class));
                for (CSVRecord record : records) {
                        stringSet.put(record.get(InputColumns.stringName), record.get(InputColumns.foreignLang));
                }
            } catch (IOException e) {
                System.out.println(String.format("Error reading csv file %s: %s", file, e));
            }
        } catch (FileNotFoundException e) {
            System.out.println(String.format("Error trying to read file %s: %s", file, e));
        }
        return stringSet;
    }

    private boolean hasStartOfTranslatableString(String line) {
        String lineNoLeadingWhtSpc = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");
        return lineNoLeadingWhtSpc.startsWith(STRING_TAG_START) && !lineNoLeadingWhtSpc.contains(TRANSLATABLE_ATTR);
    }

    private boolean isNonTranslatableString(String line) {
        String lineNoLeadingWhtSpc = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");
        if (lineNoLeadingWhtSpc.startsWith(STRING_TAG_START)) {
            if (line.contains(TRANSLATABLE_ATTR)) {
                return true;
            }
        }
        return false;
//        return line.startsWith(STRING_TAG_START) && line.contains(TRANSLATABLE_ATTR);
    }

    private String getStringName(String line) {
        // Example line: <string name=“string1”>This is a simple string.</string>
        int stringNameStartIndex = line.indexOf(DOUBLE_QUOTE) + 1;
        int stringNameEndIndex = line.indexOf(DOUBLE_QUOTE, stringNameStartIndex);
        return line.substring(stringNameStartIndex, stringNameEndIndex);
    }

    private void renameExistingFile(File source) {
        // Change name of existing xml files.
        // Example filename "./strings-es.xml" -> "./strings-es-old.xml"
        String sourceName = source.getPath();

        String destNameStart = sourceName.substring(0, sourceName.lastIndexOf('.'));
        String destNameEnd = sourceName.substring(sourceName.lastIndexOf('.'));
        String destName = String.format("%s-old%s", destNameStart, destNameEnd);
        File dest = new File(destName);

        try {
            Files.move(source.toPath(), dest.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void renameOldCreateNew(String langCode, List<String> strings) {
        // Write new string lists to files.
        // Example filename "strings-es.xml"
        File newFile = getFileWithLangCodeAndExt(langCode, FILE_EXTENSION_XML);
        renameExistingFile(newFile);

        try {
            Files.write(newFile.toPath(), strings, Charset.defaultCharset());
        } catch (IOException e) {
            System.out.println(String.format("Error writing new string %s file: %s", langCode, e));
        }
    }

    private void myImport() {

        // Language codes (i.e. en, es, zh) are the keys
        // Read both the current app string xml files and translated csv files into lists
        List<File> xmlStringFiles = getFileNamesWithExtAsList(FILE_EXTENSION_XML);
        Map<String, List<String>> xmlFilesByLine = getLinesFromFiles(xmlStringFiles);

        // Get string names and their strings from the foreign language xml files
        Set<String> xmlFileLangCodes = xmlFilesByLine.keySet();
        Map<String, Map<String, String>> foreignXmlStringSets = new HashMap<>();
        for (String xmlFileLangCode : xmlFileLangCodes) {
            // Don't get the strings from the default language
            if (!xmlFileLangCode.equals(defaultLanguageCode)) {
                foreignXmlStringSets.put(xmlFileLangCode, getStringsFromXmlFile(xmlFilesByLine.get(xmlFileLangCode)));
            }
        }

        // Get string names and their translated strings from the csv files
        List<File> translatedStringFiles = getFileNamesWithExtAsList(FILE_EXTENSION_CSV);
        Map<String, Map<String, String>> translatedStringSets = new HashMap<>();
        for (File file : translatedStringFiles) {
            translatedStringSets.put(getLanguageCode(file), getStringsFromCsvFile(file));
        }

        List<String> defaultLanguageFileAsLines = xmlFilesByLine.get(defaultLanguageCode);

        // Iterate over the foreign language codes to build the new xml files
        xmlFileLangCodes.remove(defaultLanguageCode);
        for (String langCode : xmlFileLangCodes) {
            List<String> newXmlForeignLangFileAsList = new ArrayList<>();
            boolean isLineContinuation = false;

            for (String defaultLangFileLine : defaultLanguageFileAsLines) {
                if (!isLineContinuation) {
                    if (hasStartOfTranslatableString(defaultLangFileLine)) {
                        // Get the string name from the default language file
                        String stringName = getStringName(defaultLangFileLine);
                        String stringValue = null;
                        // Check the existing foreign language file for the string
                        if (foreignXmlStringSets.get(langCode).containsKey(stringName)) {
                            stringValue = foreignXmlStringSets.get(langCode).get(stringName);
                        } else
                            // If not there, check the newly translated file
                            if (translatedStringSets.get(langCode).containsKey(stringName)) {
                                stringValue = translatedStringSets.get(langCode).get(stringName);
                            } else {
                                // If the string name is not found in either of those files,
                                // write error message into build log.
                                System.out.println(String.format("No string found for string name %s.", stringName));
                            }
                        // Create the new string
                        //"    <string name=“string1”>This is a simple string.</string>"
                        if (stringValue != null) {
                            String newString = String.format("%s%s%s%s%s", STRING_TAG_START_WITH_NAME, stringName,
                                    STRING_TAG_START_CLOSE, stringValue, STRING_TAG_END);
                            newXmlForeignLangFileAsList.add(newString);
                        }
                        if (!defaultLangFileLine.contains(STRING_TAG_END)) {
                            // Skip the lines of a multi-line string
                            isLineContinuation = true;
                        }
                    } else {
                        if (!isNonTranslatableString(defaultLangFileLine)) {
                            newXmlForeignLangFileAsList.add(defaultLangFileLine);
                        }
                    }
                } else {
                    // This line is part of a multi line string
                    if (defaultLangFileLine.contains(STRING_TAG_END)) {
                        // and this is the last line of it
                        isLineContinuation = false;
                    }
                }
            }
            // Rename the existing xml file and create the new one
            renameOldCreateNew(langCode, newXmlForeignLangFileAsList);
        }
    }

    private void myExport() {

        defaultLangStrings = new LinkedHashMap<>();
        foreignLangStringNameSets = new LinkedHashMap<>();

        // Read all xml files and, for each file, save all the strings into a Map<String, String>
        File[] filesList = new File(".").listFiles();

        for (File stringFile : filesList) {
            if (stringFile.isFile() && stringFile.getName().endsWith(FILE_EXTENSION_XML) ) {
                System.out.println(stringFile.getName());

                String fileName = stringFile.getName();
                int languageCodeStart = fileName.indexOf(LANG_CODE_SEPARATOR) + 1;
                int languageCodeEnd = languageCodeStart + 2;
                String languageCode = fileName.substring(languageCodeStart, languageCodeEnd);

                List<String> lines = null;
                try {
                    lines = Files.readAllLines(stringFile.toPath());
                } catch (IOException e) {
                    System.out.println("Error reading display files: " + e);
                }

                if (lines != null) {
                    boolean isDefaultLang = languageCode.equals(defaultLanguageCode);
                    if (!isDefaultLang) {
                        foreignLangStringNameSets.put(languageCode, new LinkedHashSet<>());
                    }
                    boolean isLineContinuation = false;
                    String stringName = "";

                    StringBuilder stringValueSb = null;
                    for (String line : lines) {
                        line = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");
                        if (!isLineContinuation) {
                            if (hasStartOfTranslatableString(line)) {
                                // Code currently assumes that all "translatable" attributes are set to false
                                // We are on a line with at least the start of a string that needs translating
                                // <string name=“string1”>This is a simple string.</string>
                                int stringNameStartIndex = line.indexOf(DOUBLE_QUOTE) + 1;
                                int stringNameEndIndex = line.indexOf(DOUBLE_QUOTE, stringNameStartIndex);
                                stringName = line.substring(stringNameStartIndex, stringNameEndIndex);

                                if (isDefaultLang) {
                                    // Get string names and strings
                                    if (line.contains(STRING_TAG_END)) {
                                        // The whole string is on one line
                                        stringValueSb = new StringBuilder(line.substring(line.indexOf(STRING_VALUE_START) + 1, line.indexOf(STRING_TAG_END)));
                                        defaultLangStrings.put(stringName, stringValueSb.toString());
                                    } else {
                                        // The string continues on the next line (hopefully)
                                        stringValueSb = new StringBuilder(line.substring(line.indexOf(STRING_VALUE_START) + 1));
                                        stringValueSb.append(LINE_FEED);
                                        isLineContinuation = true;
                                    }
                                } else {
                                    // Get just string names
                                    foreignLangStringNameSets.get(languageCode).add(stringName);
                                }
                            }
                        } else {
                            // This line is part of a multi line string
                            if (line.contains(STRING_TAG_END)) {
                                // and this is the last line of it
                                stringValueSb.append(line, 0, line.indexOf(STRING_TAG_END));
                                defaultLangStrings.put(stringName, stringValueSb.toString());
                                isLineContinuation = false;
                            } else {
                                // and this is not the last line of it
                                stringValueSb.append(line);
                                stringValueSb.append(LINE_FEED);
                            }
                        }
                    }
                }
            }
        }

        Set<String> defaultLanguageStringNames = defaultLangStrings.keySet();
        Set<String> foreignLanguageCodes = foreignLangStringNameSets.keySet();

        toBeTranslatedStringSets = new HashMap<>();
        for (String foreignLanguageCode : foreignLanguageCodes) {
            toBeTranslatedStringSets.put(foreignLanguageCode, new LinkedHashMap<>());
        }

        // For every string in the default language string map that isn't in the given foreign language file,
        // add the string name and default language string to the to-be-translated map
        for (String defaultStringName : defaultLanguageStringNames) {
            for (String foreignLangId : foreignLanguageCodes) {
                Set<String> foreignLangStringNames = foreignLangStringNameSets.get(foreignLangId);
                if (!foreignLangStringNames.contains(defaultStringName)) {
                    // Add the string name and default language string to the to-be-translated map
                    // for the current foreign language file
                    toBeTranslatedStringSets.get(foreignLangId).put(defaultStringName, defaultLangStrings.get(defaultStringName));
                }
            }
        }

        // Create csv files for all the to-be-translated Maps
        BufferedWriter outWriter = null;
        CSVPrinter outCsv = null;
        Set<String> toBeTranslatedLanguageIds = toBeTranslatedStringSets.keySet();

        for (String toBeTranslatedLangId : toBeTranslatedLanguageIds) {
            String outputFileName = String.format("./%s%s.%s", OUTPUT_FILE_PREFIX,
                    languageNames.get(toBeTranslatedLangId), FILE_EXTENSION_CSV);
            File outputFile = new File(outputFileName);

            try {
                outWriter = new BufferedWriter(new FileWriter(outputFile));
                outCsv = new CSVPrinter(outWriter, CSVFormat.EXCEL);

                // Write file header
                outCsv.printRecord("key", "English", languageNames.get(toBeTranslatedLangId));

            } catch (IOException e) {
                System.out.println("Error encountered when outputting files - " + e);
            }

            Set<String> stringNames = toBeTranslatedStringSets.get(toBeTranslatedLangId).keySet();
            Map<String, String> strings = toBeTranslatedStringSets.get(toBeTranslatedLangId);

            if (outCsv != null) {
                for (String stringName : stringNames) {
                    try {
                        outCsv.printRecord(stringName, strings.get(stringName));
                    } catch (IOException e) {
                        System.out.println("Error encountered when writing to file - " + e);
                    }
                }

                try {
                    outCsv.flush();
                    outWriter.close();
                } catch (Exception e) {
                    System.out.println("Error encountered when closing file - " + e);
                }
            }
        }

    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();

        // Initialize command line objects
        Options options = new Options();

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

        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setRequired(true);
        optionGroup.addOption(optionExport);
        optionGroup.addOption(optionImport);

        options.addOptionGroup(optionGroup);

        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("Error encountered trying to parse the command options - " + e);

            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "processAndroidStudioLangStrings", options );
        }

        ProcessAndroidStudioLangStrings app = new ProcessAndroidStudioLangStrings();

        if (commandLine != null) {
            if (commandLine.hasOption(CMD_LINE_OPTION_EXPORT)) {
                app.myExport();
            }
            if (commandLine.hasOption(CMD_LINE_OPTION_IMPORT)) {
                app.myImport();
            }
        }
    }
}
