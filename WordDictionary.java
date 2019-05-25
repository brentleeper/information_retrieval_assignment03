/*-----------------------------------------------------------------------------------
Name: 			Brent Leeper
Date: 			3/26/18
Course: 			4315
Semester: 		Spring 2018
Assignment #: 	3
Description: 	Word Dictionary for IR system. 
				Stores data (DictionaryEntry) of words that are found in a collection of files
				and relevant data. The Object is serializable and can be loaded instead of 
				re-indexing a directory 
--------------------------------------------------------------------------------------*/
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class WordDictionary implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4171548888797599634L;
	private ArrayList<DictionaryEntry> entries;
	private ArrayList<DictionaryEntry> temp;
	private List<String> sortHelper;
	private DictionaryEntry currEntry;
	private String directoryPath;
	private File[] directoryFiles;
	private int type;
	private HashMap<Integer, File> idToDoc;
	private HashMap<String, Integer> docToId;
	private ArrayList<HashMap<Integer, Integer>> zoneTotalWords;

	/**
	 * 
	 * @param directory the absolute path of the directory to be indexed
	 * @param dirFiles all of the files found in the directory at the path
	 * @param whatType a user can implement a type to help identify different types of indexes
	 */
	public WordDictionary(String directory, File[] dirFiles, int whatType) {
		entries = new ArrayList<DictionaryEntry>();
		temp = new ArrayList<DictionaryEntry>();
		sortHelper = new ArrayList<String>();
		currEntry = null;
		directoryPath = directory;
		directoryFiles = dirFiles;
		type = whatType;
		idToDoc = null;
		docToId = null;
		zoneTotalWords = null;
	}
	
	/**
	 * Used to determine what directory this WordDictionary relates to
	 * @return the path this WordDictionary index represents
	 */
	public String getDirectoryPath() {
		return directoryPath;
	}
	
	/**
	 * 
	 * @param dirFiles - the files that are indexed within the WordDictionary
	 */
	public void setDirectoryFiles(File[] dirFiles) {
		directoryFiles = dirFiles;
	}
	
	/**
	 * 
	 * @return the files currently indexed by this WordDictionary
	 */
	public File[] getDirectoryFiles() {
		return directoryFiles;
	}
	
	/**
	 * 
	 * @return the type of WordDictionary specified by the user
	 */
	public int getWhatType() {
		return type;
	}

	/**
	 * used to add entries to the dictionary 
	 * if anther entry containing the same word already exists 
	 * that entry will be updated
	 * 
	 * @param word
	 * @param docID
	 */
	public void addEntry(String word, int docID, double weight, int zone) {
		int index = wordIndex(word);
		if (index == -1) {
			currEntry = new DictionaryEntry(word, toSoundex(word));
			currEntry.updateDocID(docID, weight, zone);
			entries.add(currEntry);
			sortHelper.add(word);
		} else {
			currEntry = entries.get(index);
			currEntry.updateDocID(docID, weight, zone);
			entries.set(index, currEntry);
		}
	}

	/**
	 *
	 * @return number of entries in the dictionary
	 */
	public int wordCt() {
		return entries.size();
	}

	/**
	 * @param word
	 * @param fileLookup
	 * @return string containing the word and all documents the word is found in
	 */
	public String printEntry(String word, HashMap<Integer, File> fileLookup) {
		String output = word + " \t<- found in documents:" + System.lineSeparator()
				+ entries.get(wordIndex(word)).buildString(fileLookup);
		return output;
	}

	/**
	 * 
	 * @param word
	 * @return the index position within entries of the entry containing word
	 */
	private int wordIndex(String word) {
		int rv = -1;
		int ct = 0;
		for (DictionaryEntry currEntry : entries) {
			if (currEntry.getWord().equals(word)) {
				rv = ct;
				break;
			}
			ct++;
		}
		return rv;
	}

	/**
	 * 
	 * @return an ArrayList<String> of words held in the dictionary's entries
	 */
	public ArrayList<String> getWords() {
		ArrayList<String> rv = new ArrayList<String>();
		for (DictionaryEntry entry : entries) {
			rv.add(entry.getWord());
		}
		return rv;
	}

	/**
	 * Sorts the dictionary's current entries alphabetically
	 */
	public void sortAlphabetically() {
		Collections.sort(sortHelper);

		DictionaryEntry curr = null;

		for (String word : sortHelper) {
			curr = entries.get(wordIndex(word));
			temp.add(curr);
		}
		entries = temp;
		temp = null;
	}

	/**
	 * 
	 * @param wordIndex
	 * @param ID
	 * @return if the entry at the given index contains the given id
	 */
	public boolean wordInDoc(String word, int ID) {
		int index = wordIndex(word);
		return entries.get(index).inDoc(ID);
	}

	/**
	 * 
	 * @param word
	 * @return an ArrayList of ints that the given word is found in
	 */
	public ArrayList<DocumentInfo> containedIn(String word) {
		int index = wordIndex(word);
		ArrayList<DocumentInfo> rv = null;
		if (index != -1)
			rv = entries.get(index).docInfoList();
		return rv;
	}

	/**
	 * 
	 * @param word
	 * @return generates a soundex hash for a given word
	 */
	private String toSoundex(String word) { 
		if (word == null)
			return null;
		word = word.toUpperCase();
		char firstChar = word.charAt(0);

		word = word.substring(1, word.length());
		word = word.replaceAll("[AEIOUHWY]", "0");
		word = word.replaceAll("[BFPV]", "1");
		word = word.replaceAll("[CGJKQSXZ]", "2");
		word = word.replaceAll("[DT]", "3");
		word = word.replaceAll("[L]", "4");
		word = word.replaceAll("[MN]", "5");
		word = word.replaceAll("[R]", "6");

		StringBuilder tempWord = new StringBuilder(word);

		for (int i = 1; i < tempWord.length(); i++) {
			if (tempWord.charAt(i) == tempWord.charAt(i - 1)) {
				tempWord.deleteCharAt(i);
			}
		}

		word = tempWord.toString();

		word = word.replaceAll("[0]", "");

		word = firstChar + word;

		while (word.length() < 4) {
			word += '0';
		}

		return word.substring(0, 4);
	}

	/**
	 * 
	 * @param word
	 * @return an ArrayList of Strings, each a word that matches the given word's soundex hash
	 */
	public ArrayList<String> phoneticCheck(String word) {
		ArrayList<String> rv = new ArrayList<String>();
		String wordHash = toSoundex(word);

		for (DictionaryEntry currEntry : entries) {
			if (currEntry.getSoundex().equals(wordHash)) {
				rv.add(currEntry.getWord());
			}
		}
		return rv;
	}

	/**
	 * 
	 * @param word
	 * @return if the given word is contained in this dictionary
	 */
	public boolean contains(String word) {
		boolean rv = false;
		for (DictionaryEntry currEntry : entries) {
			if (currEntry.getWord().equals(word)) {
				rv = true;
				break;
			}
		}
		return rv;
	}
	
	/**
	 * 
	 * @param word
	 * @return the number of word appearances across the document set
	 */
	public int appearsInDocCt(String word) {
		return entries.get(wordIndex(word)).inNumOfDocs();		
	}
	
	/**
	 * 
	 * @return the number of documents this WordDictionary currently has indexed
	 */
	public int totalNumOfDocs() {
		return directoryFiles.length;
	}
	
	/**
	 * Used to hold relevant data for serialization 
	 * @param itd
	 * @param dti
	 * @param ztw
	 */
	public void storeRelevantData(HashMap<Integer, File> itd, HashMap<String, Integer> dti, ArrayList<HashMap<Integer, Integer>> ztw) {
		idToDoc = itd;
		docToId = dti;
		zoneTotalWords = ztw;
	}
	
	/**
	 * 
	 * @return the relevant (docID to File) hashmap, used for IR referencing
	 */
	public HashMap<Integer, File> getIdToDoc() {
		return idToDoc;
	}
	
	/**
	 * 
	 * @return the relevant (file name to docID) hashmap, used for IR referencing
	 */
	public HashMap<String, Integer> getDocToId(){
		return docToId;
	}
	
	/**
	 * 
	 * @return the relevant List of (zone to word count) hashmaps, used for IR referencing
	 */
	public ArrayList<HashMap<Integer, Integer>> getZoneTotalWords(){
		return zoneTotalWords;
	}
	
	/**
	 * Useful for removing a reference to a file that has been removed from the file system
	 * @param file - the file to be dereferenced by this WordDictionary
	 */
	public void removeRefrences(File file) {
		int i = 0;
		for (File currFile : directoryFiles) {
			if(currFile.getAbsolutePath().equals(file.getAbsolutePath()))
			{
				directoryFiles[i] = directoryFiles[directoryFiles.length - 1];
				directoryFiles[directoryFiles.length - 1] = null;
				directoryFiles = Arrays.copyOf(directoryFiles, directoryFiles.length - 1);
				Arrays.sort(directoryFiles);
				break;
			}
			i++;
		}
	}
}
