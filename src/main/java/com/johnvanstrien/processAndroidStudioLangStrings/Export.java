package com.johnvanstrien.processAndroidStudioLangStrings;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static com.johnvanstrien.processAndroidStudioLangStrings.ProcessAndroidStudioLangStrings.*;

public class Export {

    static void process() {
        Map<String, String> defaultLangStrings;
        Map<String, Set<String>> foreignLangStringNameSets;
        Map<String, Map<String, String>> toBeTranslatedStringSets;

        defaultLangStrings = new LinkedHashMap<>();
        foreignLangStringNameSets = new LinkedHashMap<>();

        // Read all xml files and, for each file, save all the strings into a Map<String, String>
        File[] filesList = new File(".").listFiles();

        if ( !(filesList != null && filesList.length > 0) ) {
            System.out.println("So sad - no xml files found.");
            return;
        }

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
                outCsv.printRecord(STRING_NAME, languageNames.get(defaultLanguageCode), languageNames.get(toBeTranslatedLangId));

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

    private static boolean hasStartOfTranslatableString(String line) {
        String lineNoLeadingWhtSpc = line.replaceFirst(REGEX_LEADING_WHITESPACE, "");
        return lineNoLeadingWhtSpc.startsWith(STRING_TAG_START) && !lineNoLeadingWhtSpc.contains(TRANSLATABLE_ATTR);
    }

}
