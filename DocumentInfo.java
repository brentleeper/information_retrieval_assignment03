/*-----------------------------------------------------------------------------------
Name: 			Brent Leeper
Date: 			3/26/18
Course: 			4315
Semester: 		Spring 2018
Assignment #: 	3
Description: 	Document Information for IR system. 
				A DocumentInfo object holds information to reference the actual document 
				it represents. It also contains information collected from the reference 
				document that can be used to calculate it's rank for a given query term.	
				
					Note: each DictionaryEntry will maintain a set of DocumentInfo objects
					in an inverted index format where the word references several of these
					objects. Therefore, different entries may contain a DocumentInfo object
					that references the same document
--------------------------------------------------------------------------------------*/

import java.io.Serializable;

public class DocumentInfo implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3373205853420880507L;
	private int id;
	private double[] w;
	private int[] o;
	
	
	/**
	 * 
	 * @param docID the documents reference ID
	 * @param weight	an array of size i where i is the number of document zones
	 * @param occurrences an array of size i where i is the number of document zones and occurrences[i]  equals the number of word occurrences in the zone i
	 */
	public DocumentInfo(int docID, double[] weight, int[] occurrences)
	{
		id = docID;
		w = weight;
		o = occurrences;
	}
	
	/**
	 * 
	 * @return id to reference the actual document
	 */
	public int getID() {
		return id;
	}
	
	/**
	 * 
	 * @param weight of the specific document zone
	 * @param zone - related to the zone of text within a document
	 */
	public void setWeight(double weight, int zone) {
		w[zone] = weight;
	}
	
	/**
	 * 
	 * @param zone - input the zone number or -1 for total weight across all zones
	 * @return the weight for the specified zone
	 */
	public double getWeight(int zone) {
		double rv = 0;
		
		if(zone != -1)
			rv = w[zone];
		else {
			for(int i = 0; i < w.length; i++)
			{
				rv += w[i];
			}
		}
		return rv;
	}
	
	
	/**
	 * 
	 * @param zone - input the zone number or -1 for total occurrences across all zones
	 * @return the number of word occurrences within the specified zone
	 */
	public int getOccurrences(int zone) {
		int rv = 0;
		if(zone != -1)
			rv = o[zone];	
		else
		{
			for(int i = 0; i < o.length; i++)
				rv += o[i];
		}
		
		return rv;
	}
	
	/**
	 * 
	 * @param occurrences - the number of occurrences in the specified zone
	 * @param zone - the zone the specified number of occurrences of the term appeared
	 */
	public void setOccurrences(int occurrences, int zone) {
		o[zone] = occurrences;
	}
	
}
