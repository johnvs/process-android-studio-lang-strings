## processAndroidStudioLangStrings
This project helps with processing of foreign language (FL) strings for Android apps. It helps by exporting strings that need 
translation and importing the translated strings. 

There is currently little error checking, and things like the default language are currently hard coded (to English 
in this case).

There are several steps required for a complete translation transaction, some of which take place before executing this 
utility and some afterwards.

### Exporting strings to be translated
To create files with the strings that need to be translated, follow these steps:
1. Add a bunch of new strings to the default language file (.../res/values/strings.xml).
2. Create a directory for the operation (outside of the Android project directory structure).
3. Copy all the string files from the app into this new directory.
4. Execute pASLS with option -e (export) on the string file directory.
5. This will result in a new file for each FL string file, named ToBeTranslated-\<language>.csv, for example 
ToBeTranslated-Chinese.csv.
6. Open the CSV files in Excel. They will have two columns, named "String Name" and \<default language name>, containing 
all the string names and default strings need translation.
7. Add any notes for the translator, like explanations for strange looking characters like "\n" or "%1$s" in the middle 
of a string and how they should treat them. You also might want to lock the first two columns so they don't get 
"accidentally" modified when the files are out of your control.
8. Save the file as an Excel file.
9. Send the files to the translator(s).

### Importing translated strings
After receiving the translated Excel files back from the translator(s), follow these steps:
1. Open the translated files in Excel and save them as CSV files.
2. Remove any notes from the files so that they contain only the lines of translated strings.
3. Save and close the files.
4. Rename the CSV files to "translated-xx.csv", where xx is the language code, i.e. en for English, es for 
Spanish or zh for Chinese.
5. Again create a directory for this operation and copy these csv files to it.
6. Copy the default and FL xml files to this directory.
7. Execute pASLS with option -i (import) on the string file directory.
8. pASLS will rename all the FL XML files to strings-xx-old.xml, where xx is the language code. It will then create new
XML files (one for each FL), which are the combination of all the previously translated strings from the existing XML 
files and the newly translated strings from the CSV files.
9. Copy the new XML string files into the Android project, removing the "-xx" language code suffix, so that all the 
files are named "strings.xml", and located in the appropriate .../res/values-xx directory. 

It's that easy!!
