package com.johnvanstrien.processAndroidStudioLangStrings;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import static com.johnvanstrien.processAndroidStudioLangStrings.ProcessAndroidStudioLangStrings.*;

public class Import {

    static void process() {
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
                                System.out.printf("No string found for string name %s.%n", stringName);
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

    // Used to read data from CSV files
    private enum InputColumns {
        stringName,
        english,
        foreignLang;
    }

    // Find all the files with the given extension and return them as a List<File>
    private static List<File> getFileNamesWithExtAsList(String fileExt) {
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

    private static File getFileWithLangCodeAndExt(String langCode, String fileExt) {
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

    private static String getLanguageCode(File file) {
        String fileName = file.getName();
        int languageCodeStart = fileName.indexOf(LANG_CODE_SEPARATOR) + 1;
        int languageCodeEnd = languageCodeStart + 2;
//        String langCode = fileName.substring(languageCodeStart, languageCodeEnd);
        return fileName.substring(languageCodeStart, languageCodeEnd);
    }

    private static Map<String, List<String>> getLinesFromFiles(List<File> files) {
        Map<String, List<String>> fileLineMap = new HashMap<>();
        for (File stringFile : files) {
            String languageCode = getLanguageCode(stringFile);
            try {
                fileLineMap.put(languageCode, Files.readAllLines(stringFile.toPath()));
            } catch (IOException e) {
                System.out.printf("Error reading lines from file %s: %s%n", stringFile.getName(), e);
            }
        }
        return fileLineMap;
    }

    private static Map<String, String> getStringsFromXmlFile(List<String> fileByLine) {
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

    private static Map<String, String> getStringsFromCsvFile(File file) {
        Map<String, String> stringSet = new HashMap<>();
        try {
            Reader fileReader = new FileReader(file);
            try {
                CSVParser records = new CSVParser(fileReader, CSVFormat.EXCEL.withHeader(InputColumns.class));
                for (CSVRecord record : records) {
                        stringSet.put(record.get(InputColumns.stringName), record.get(InputColumns.foreignLang));
                }
            } catch (IOException e) {
                System.out.printf("Error reading csv file %s: %s%n", file, e);
            }
        } catch (FileNotFoundException e) {
            System.out.printf("Error trying to read file %s: %s%n", file, e);
        }
        return stringSet;
    }

    private static boolean hasStartOfTranslatableString(String line) {
        String lineNoLeadingWhtSpc = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");
        return lineNoLeadingWhtSpc.startsWith(STRING_TAG_START) && !lineNoLeadingWhtSpc.contains(TRANSLATABLE_ATTR);
    }

    private static boolean isNonTranslatableString(String line) {
        String lineNoLeadingWhtSpc = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");
        if (lineNoLeadingWhtSpc.startsWith(STRING_TAG_START)) {
//            if (line.contains(TRANSLATABLE_ATTR)) {
            return line.contains(TRANSLATABLE_ATTR);
//            }
        }
        return false;
//        return line.startsWith(STRING_TAG_START) && line.contains(TRANSLATABLE_ATTR);
    }

    private static String getStringName(String line) {
        // Example line: <string name=“string1”>This is a simple string.</string>
        int stringNameStartIndex = line.indexOf(DOUBLE_QUOTE) + 1;
        int stringNameEndIndex = line.indexOf(DOUBLE_QUOTE, stringNameStartIndex);
        return line.substring(stringNameStartIndex, stringNameEndIndex);
    }

    private static void renameExistingFile(File source) {
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

    private static void renameOldCreateNew(String langCode, List<String> strings) {
        // Write new string lists to files.
        // Example filename "strings-es.xml"
        File newFile = getFileWithLangCodeAndExt(langCode, FILE_EXTENSION_XML);
        renameExistingFile(newFile);

        try {
            Files.write(newFile.toPath(), strings, Charset.defaultCharset());
        } catch (IOException e) {
            System.out.printf("Error writing new string %s file: %s%n", langCode, e);
        }
    }
}
