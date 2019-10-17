package com.johnvanstrien.extractAndroidStudioLangStrings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class ExtractAndroidStudioLangStrings {

    private static final String LANG_ID_SEPARATOR = "-";

    private Map<String, Map<String, String>> dictionaries;

    private void run(String[] args) {

        dictionaries = new HashMap<>();

        // Read all xml files and, for each file, save all the strings into a Map<String, String>
//        File curDir = new File(".");
//        File[] filesList = curDir.listFiles();

        File[] filesList = new File(".").listFiles();

        for (File f : filesList) {
            if (f.isFile() && f.getName().endsWith("xml") ) {
                System.out.println(f.getName());

                String fileName = f.getName();
                int languageIdStart = fileName.indexOf(LANG_ID_SEPARATOR) + 1;
                int languageIdEnd = languageIdStart + 2;
//                String language = fileName.substring(fileName.indexOf(FILENAME_SEPARATOR) + 1, 2);
                String languageId = fileName.substring(languageIdStart, languageIdEnd);
                dictionaries.put(languageId, new HashMap<>());

                try {
//                    FileReader fileReader = new FileReader(f.getName());
                    List<String> lines = Files.readAllLines(f.toPath());

                    for (String line : lines) {
                        if (line.contains("<string name")) {
                            // <string name=“string1”>This is a simple string.</string>
                            String stringName = line.substring(line.indexOf('"') + 1);
                            stringName = stringName.substring(0,stringName.indexOf('"'));

                            String stringValue = line.substring(line.indexOf('>') + 1, line.indexOf("</"));

                            dictionaries.get(languageId).put(stringName, stringValue);

                        }
                    }

//            list.forEach(line -> System.out.println(line));
                } catch (IOException e) {
                    System.out.println("Error reading display files: " + e);
                }
            }
        }

        // For every foreign language dictionary,
        //	for every key in the default language dictionary (en/English in this case),
        //		if that key is not in the foreign language dict, add a line to that language’s CSV file

    }

/*
    public enum LanguageNames {
        en ("English"),
        es  ("Spanish"),
        zh ("Chinese");

        private String id;

        LanguageNames(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getValue() {
            return id;
        }

//        private static List<LanguageNames> values = new ArrayList<>();
        private static Map<String, LanguageNames> values = new HashMap<>();
        static {
            for (LanguageNames level : LanguageNames.values()) {
                values.put(level.id, level);
            }
        }

        public static LanguageNames valueOf(int id) {
            return values.get(id);
        }

    }
*/

//    private static final List<String> LEVEL_NAMES =
//            new ArrayList<>(Arrays.asList("Cross", "Long"));

    public static void main(String[] args) {
        ExtractAndroidStudioLangStrings app = new ExtractAndroidStudioLangStrings();
        app.run(args);
    }
}
