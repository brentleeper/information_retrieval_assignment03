/*-----------------------------------------------------------------------------------
Name: 			Brent Leeper
Date: 			3/26/18
Course: 			4315
Semester: 		Spring 2018
Assignment #: 	1
Description: 	Dictionary Entry for IR system. 
				A Dictionary entry holds a single word and maintains a collection
				of documents that the word is found in, as well as number of occurrences 
				of the word for each document			
--------------------------------------------------------------------------------------*/

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class DictionaryEntry implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 6297049253426856432L;
	private String word;
	private String wordInDocs;
	private ArrayList<DocumentInfo> docData;
	private int inDocCt;
	private ArrayList<Integer> docList;
	private String soundexHash; 
	
	
	/**
	 * constructor
	 * @param w the word
	 * @param hash the soundex hash
	 */
	public DictionaryEntry(String w, String hash) {
		word = w;
		docData = new ArrayList<DocumentInfo>();
		inDocCt = 0;
		docList = new ArrayList<Integer>();
		soundexHash = hash;
	}

	/**
	 * 
	 * @return the entry's word 
	 */
	public String getWord() {
		return word;
	}

	/**
	 * 
	 * @param id
	 * @return the number of times the word occurs in the given document ID
	 */
	public int getDocCt(int id, int zone) {
		return docData.get(0).getOccurrences(zone);
	}

	/**
	 * 
	 * @return the total number of times the word occurred across all documents
	 */
	public int inNumOfDocs() {
		return inDocCt;
	}

	/**
	 * Used to update the collection of document IDs that the word is found in
	 * if the word had previously been found in a document with the same ID
	 * increment the word's number of occurrences for that document ID
	 * @param id
	 */
	public void updateDocID(int id, double weight, int zone) {
		
		if (!inDoc(id)) {
			int[] occuranceZones = {0, 0, 0};
			double[] weightZones = {0, 0, 0};
			DocumentInfo currDoc = new DocumentInfo(id, weightZones, occuranceZones);
			currDoc.setOccurrences(1, zone);
			currDoc.setWeight(weight, zone);
			docData.add(currDoc);
			docList.add(id);
			inDocCt++;
		} else {
			int index = docIndex(id);
			DocumentInfo currDoc = docData.get(index);
			currDoc.setOccurrences(currDoc.getOccurrences(zone) + 1, zone);
			currDoc.setWeight(currDoc.getWeight(zone) + weight, zone);
			docData.set(index, currDoc);
		}
	}

	/**
	 * 
	 * @return an ArrayList of ints representing all the document IDs this entry is found in
	 */
	public ArrayList<DocumentInfo> docInfoList() {
		return docData;
	}

	/**
	 * 
	 * @param fileLookup
	 * @return a string containing all document IDs the word is found in
	 * as well as the name of the document and the number of occurrences of the word in the document
	 */
	public String buildString(HashMap<Integer, File> fileLookup) {

		wordInDocs = ""; // must be global for modification within 'forEach' loop body

		for(DocumentInfo currDoc : docData)
		{
			if (currDoc.getOccurrences(-1) > 1)
				wordInDocs += "\tDocID: " + currDoc.getID() + " \t(" + fileLookup.get(currDoc.getID()).getName() + ")\t: \t" + currDoc.getOccurrences(-1)
						+ " occurrences" + System.lineSeparator();
			else
				wordInDocs += "\tDocID: " + currDoc.getID() + " \t(" + fileLookup.get(currDoc.getID()).getName() + ")\t: \t" + currDoc.getOccurrences(-1)
						+ " occurrence" + System.lineSeparator();
		}
		return wordInDocs;
	}

	/**
	 * 
	 * @param ID
	 * @return if the word is found in the given document ID
	 */
	public boolean inDoc(int ID) {
		boolean rv = false;
		
		if(docIndex(ID) != -1)
		{
			rv = true;
		}
		
		return rv;
	}
		
	/**
	 * 
	 * @return the soundexHash of this entry
	 */
	public String getSoundex() {
		return soundexHash;
	}
	
	/**
	 * 
	 * @param id
	 * @return -1 if the id is not represented within the array of DocumentInfo or the index location of the DocumentInfo with the given id
	 */
	private int docIndex(int id) {
		int rv = -1;
		for(int i = 0; i < docData.size(); i++)
		{
			if(docData.get(i).getID() == id)
			{
				rv = i;
				break;
			}
		}
		return rv;
	}
}