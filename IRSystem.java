/*-----------------------------------------------------------------------------------
Name: 			Brent Leeper
Date: 			3/23/18
Course: 			4315
Semester: 		Spring 2018
Assignment #: 	2
Description: 	Creates a search-able inverted index and bi-word index from files
				within a user specified directory. Includes a GUI that allows
				for the input of search queries, each query is evaluated for possible
				misspellings and checked against a soundex as needed. Results are ranked 
				with a combination of TF-IDF and Weighted Zone Scoring and the top 5 
				ranking documents for a query are displayed to the GUI where a user can 
				select and open a given file. Additionally, on user selection of a 
				directory path the system will check for an existing index and attempt
				to load it instead of re-indexing. Once either the index is built or the
				index is loaded, a thread will run periodically to check for new files in
				the user specified directory and update the index in the background if 
				new files are found. If a user attempts to open a file that no longer 
				exist, the file reference will be removed from the system and results 
				will be updated. 
				
				Zone weights are as follows:
				
				Title (first line): 0.5
				First Sentence: 	0.3
				Body: 				0.2
--------------------------------------------------------------------------------------*/

import java.io.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.concurrent.Semaphore;

public class IRSystem {

	private static JFrame frame;
	private JButton btnSearch;
	private String inputPath; 
	private JList<String> resultList = null;
	private JScrollPane resultScroll = null;
	private HashMap<Integer, File> idToDoc;
	private HashMap<String, Integer> docToId;
	private ArrayList<HashMap<Integer, Integer>> zoneTotalWords;
	private JTextField searchBar;
	private DefaultListModel<String> resultModel = null;
	private WordDictionary dictionary;
	private WordDictionary biWordDict;
	private boolean searchBarHelper;
	private Semaphore alertManager, indexUpdate;

	public void start() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 		// used to try to match the current
																					// system's
																					// native look and feel
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException 				// will use default if the
				| UnsupportedLookAndFeelException e1) {													// look and feel cannot be
			System.err.println("Unable to match system look and feel, continuing with default...");		// obtained from the
		}																								// UIManager

		alertManager = new Semaphore(1);	 // used to prevent concurrent display of alerts
		indexUpdate = new Semaphore(1);
		frame = new JFrame("Search Pro"); 						// set up
		frame.setVisible(false); 								// main jframe 'frame'
		frame.setSize(new Dimension(525, 525)); 					// set
		frame.setResizable(false); 								// and restrict size
		frame.setLocationRelativeTo(null); 						// center of main screen
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 	//
		frame.getContentPane().setLayout(null); //

		JButton btnOpen = new JButton("Open"); 															// set up open btn
		btnOpen.addActionListener(new ActionListener() { 												//
			public void actionPerformed(ActionEvent arg0) { 												//on click
				if(resultList.isSelectionEmpty())
				{
					alert("Please Select A Document", frame, Color.YELLOW);
				}
				else {
					String doc = resultList.getSelectedValue().toString();
					try { 																					// open the selected
						System.out.println(idToDoc.get(docToId.get(doc)).getAbsolutePath());
						File selectedFile = new File(idToDoc.get(docToId.get(doc)).getAbsolutePath()); 		// document
						System.out.println("Opening: " + selectedFile.getAbsolutePath());
						Desktop.getDesktop().open(selectedFile); 											//
					} catch (Exception e) {
						e.printStackTrace();//if a file cannot be opened by the system, it is irrelevant 
						JOptionPane.showMessageDialog(frame, "Unable To Open The Document: The File No Longer Exists"); 			
						dictionary.removeRefrences(new File (idToDoc.get(docToId.get(doc)).getAbsolutePath())); 			// remove all references to the document in the system
						biWordDict.removeRefrences(new File (idToDoc.get(docToId.get(doc)).getAbsolutePath()));			//
						System.out.println("Reference Removed for: " + idToDoc.get(docToId.get(doc)).getName());			//
						idToDoc.remove(docToId.get(doc));																//
						docToId.remove(doc);																				//	
						btnSearch.doClick();																				// and resubmit the query for updated results
						
						Thread backgroudSave = new Thread(new Runnable() { 							//save the index in the background
							public void run() {	
								
								alert("Removing File Reference From Index", frame, Color.ORANGE);		//alert the user of the change
								
								File workingDir = new File(inputPath);
								ObjectOutputStream oos;
								
								try {
									indexUpdate.acquire();
								} catch (InterruptedException e)	{}
								
								try {			
									oos = new ObjectOutputStream(new FileOutputStream("./index/" + workingDir.getName() + "_t" + dictionary.getWhatType() + ".INDEX"));
									oos.writeObject(dictionary);
									oos.close();
									oos = new ObjectOutputStream(new FileOutputStream("./index/" + workingDir.getName() + "_t" + biWordDict.getWhatType() + ".INDEX"));
									oos.writeObject(biWordDict);
									oos.close();
								} catch (FileNotFoundException e1) {
									e1.printStackTrace();
								} catch (IOException e2) {
									e2.printStackTrace();
								}
								indexUpdate.release();
								
								alert("The Index Is Up To Date", frame, Color.ORANGE);		//alert the user upon completion
							}
						});
						backgroudSave.start();
					} 																						
				}
			} 																							//
		});																								//
		btnOpen.setBounds(10, 457, 499, 29); 															// add the button
		frame.getContentPane().add(btnOpen); 															// to the main frame

		resultModel = new DefaultListModel<String>(); // used to dynamically change the contents of the result list
		resultList = new JList<String>(resultModel); 			// set up JList for displaying search results
		resultList.addKeyListener(new KeyAdapter() { 			//
			@Override 											// listen for
			public void keyPressed(KeyEvent key) { 				// key down events while the list is in focus
				if (key.getKeyCode() == KeyEvent.VK_ENTER) { 	// if the key pressed is 'ENTER'
					btnOpen.doClick(); 							// virtually click the open button
				} 												// to open the selected document
			} 													//
		});													 	//

		resultScroll = new JScrollPane(resultList); 			// set up JScrollPane passing the resultList
		resultScroll.setBounds(10, 91, 499, 359); 			// allowing for scrolling through a list larger than what can be
															// displayed
		frame.getContentPane().add(resultScroll);			// add the scroll to the main frame

		btnSearch = new JButton("Search"); 															// set up search button
		btnSearch.addActionListener(new ActionListener() { 											//
			public void actionPerformed(ActionEvent e) { 											// on click
	
				if(!indexUpdate.tryAcquire())			//if the index is unavailable 
				{
					JOptionPane.showMessageDialog(frame, "Please Wait For The Index To Update");
				}
				else {
					resultModel.clear(); 																		// clear previous resultModel, removing displayed elements
					String query = searchBar.getText().toLowerCase().trim(); 										// get text from the search bar and normalize
					ArrayList<String> phoneticResults = new ArrayList<String>(); 									//
																												//
					if (searchBar.getForeground().equals(Color.BLACK)) { 											// if the text color is black, indicating the user
																												// has entered text
						String alertText = ""; 																	//
						ArrayList<DocumentInfo> results = null; 													//
						ArrayList<DocumentInfo> resultsFullPhrase = null;											//
						if (!query.contains(" ")) { 																// if the query is only one word
							results = dictionary.containedIn(query); 											// ask the dictionary for what documents the word is
																												// found in
							if (results == null) { 																// if no results
								System.out.println("Query '" + query + "' not found..."); 						//
								alertText = "Changed '" + query + "' to '"; 										//
								phoneticResults = dictionary.phoneticCheck(query); 								// check the dictionary for words with
																												// the same soundex hash
								if (phoneticResults.size() > 1) {												// if there is more than one possible correction
									alert("Multiple Possible Corrections", frame, Color.YELLOW);					// notify the user
									query = (String) JOptionPane.showInputDialog(frame, "Did you mean...",		// prompt for user selection of a possible correction
											query + ": Not Found", JOptionPane.QUESTION_MESSAGE, null,			//
											phoneticResults.toArray(), phoneticResults.get(0));					//
									if (query != null)															// as long as the user did not click cancel
										searchBar.setText(query);												// change the search bar to match the users selection
									else																			//or if cancel was clicked
										searchBar.setText("");													//remove all text from the search bar
								} else if (phoneticResults.size() == 1) {										//	if there is only one possible correction
									query = phoneticResults.get(0);												//	
								} else {																			// if there are no possible corrections
									query = null;																//
								}																				//
								alertText += query + "'"; 														//
								System.out.println("trying '" + query + "' instead."); 							//
																												//
								if (query != null) { 															// if a word with the same hash is found and/or selected by the user
									searchBar.setText(query); 													// change the search bar text to the found word
									results = dictionary.containedIn(query); 									// ask the dictionary for what documents the new word is found in
									alert(alertText, frame, Color.GREEN); 										// notify the user of the word correction
								} 																				//
							} 																					//
							if (results != null) {																// if at this point, after a potential phoneticCheck, the results are not null
								boolean hasElementsDisplayed = false;											//
								for (int result : rankedResults(query, results)) { 								//
									if(idToDoc.get(result) != null) {											//
										resultModel.addElement(idToDoc.get(result).getName()); 					// add each result to the resultModel to dynamically update the JList
										hasElementsDisplayed = true;												//
									}																			//
								} 																				//
								if(!hasElementsDisplayed)														//
									alert("No Results Found", frame, Color.RED); 								// notify the user that no results were found
							} else 																				// otherwise
								alert("No Results Found", frame, Color.RED); 									// notify the user that no results were found
						} else { 																				//
							results = new ArrayList<DocumentInfo>();												// if the query is more than one word
																												//
							resultsFullPhrase = biWordDict.containedIn(query); 									// ask the bi word dictionary for documents containing the full phrase
																												//
							String[] splitQuery = query.split(" ", 3); 											// split the query
							String  query2Before = splitQuery[1]; 												//
							String query1 = null, query2 = null; 												//
																												//
							if (resultsFullPhrase == null) { 													// if the full phrase was not found
								System.out.println("Query '" + query + "' not found..."); 						//
								alertText = "Changed '" + query + "' to '"; 										//
																												//
								if (dictionary.contains(splitQuery[0])) { 										// phonetic check both words separately
									query1 = splitQuery[0]; 														//
								} else { 																		//
									phoneticResults = dictionary.phoneticCheck(splitQuery[0]); 					// check the dictionary for words with the same soundex hash
									if (phoneticResults.size() > 1) {											// 
										alert("Multiple Possible Corrections", frame, Color.YELLOW);				// do the same as above
										query1 = (String) JOptionPane.showInputDialog(frame, 						//	notifying 
												"Did you mean...",query + ": Not Found",							//	prompting
												JOptionPane.QUESTION_MESSAGE, null,								//	user
												phoneticResults.toArray(), phoneticResults.get(0));				//
										if (query1 != null)														//
											searchBar.setText(query1 + " " + splitQuery[1]);						//	and updating
										else																		//	search bar accordingly
											searchBar.setText("");												//
									} else if (phoneticResults.size() == 1) {									//
										query1 = phoneticResults.get(0);											//
									} else {																		//
										query1 = null;															//
									}																			//	
								} 																				//
								if (dictionary.contains(splitQuery[1])) { 										//
									query2 = splitQuery[1]; 														//
								} else if (query1 != null) { 													//
									phoneticResults = dictionary.phoneticCheck(splitQuery[1]);					// check the dictionary for words with the same soundex hash
									if (phoneticResults.size() > 1) {											//
										alert("Multiple Possible Corrections", frame, Color.YELLOW);				//
										query2 = (String) JOptionPane.showInputDialog(frame,						//	
												"Did you mean...",query1 + " " + query2Before + ": Not Found",	//
												JOptionPane.QUESTION_MESSAGE, null,								//
												phoneticResults.toArray(), phoneticResults.get(0));				//
										if (query1 != null && query2 != null)									//
											searchBar.setText(query1 + " " + query2);							//
										else																		//
											searchBar.setText("");												//
									} else if (phoneticResults.size() == 1) {									//
										query2 = phoneticResults.get(0);											//
									} else {																		//
										query2 = null;															//
									}																			//
								} 																				//
								alertText += query1 + " " + query2 + "'"; 										//
																												//
								if (query1 != null && query2 != null && !query.equals(query1 + " " + query2)) { 	// if the query was modified by a phonetic check,
									alert(alertText, frame, Color.GREEN); 										//
									searchBar.setText(query1 + " " + query2); 									//
								} 																				//
								resultsFullPhrase = biWordDict.containedIn(query1 + " " + query2); 				// ask the bi word dictionary for documents containing the modified phrase
							}																					//
							else																					//
							{																					//
								query1 = splitQuery[0];															//
								query2 = splitQuery[1];															//
							}																					//
							if (!searchBar.getText().equals("")) {												//
								if (resultsFullPhrase != null) { 												// if there are any results
									for (DocumentInfo result : resultsFullPhrase) { 								//	
										results.add(result); 													// and add to the results array
									} 																			//
								} 																				//
								boolean hasElementsDisplayed = false;//											//
								if (!results.isEmpty()) { 														//
									for (int result : rankedResults(query1 + " " + query2, results)) {			// add ranked results to the resultModel if results 
										try {																	// contains any results
										resultModel.addElement(idToDoc.get(result).getName()); 					//
										hasElementsDisplayed = true;												//
										}catch (NullPointerException npe) {}										// skip result on exception 
									}																			//
									if(!hasElementsDisplayed)													//
										alert("No Results Found", frame, Color.RED);//							//
								} else 																			//
									alert("No Results Found", frame, Color.RED); 								// notify the user if no results are found
							} else 																				//
								alert("No Results Found", frame, Color.RED);										//
						}																						//
						frame.repaint(); 																		// repaint to ensure any changes made to the GUI are displayed
					} 																							//
					indexUpdate.release();																		// release the index
				}																								//
			} 																									//
		}); 																										//
		btnSearch.setBounds(10, 51, 499, 29);																	//add the search button
		frame.getContentPane().add(btnSearch); 																	//to the main frame

		searchBar = new JTextField();							// set up search bar
		searchBar.setText("Enter Your Query"); 					// with initial text
		searchBar.addMouseListener(new MouseAdapter() {			//
			@Override 											// on click
			public void mouseClicked(MouseEvent e) { 			//
				if (searchBar.getText().equals("")) { 			// if the search bar is empty
					searchBar.setText("Enter Your Query"); 		// display prompt
					searchBar.setForeground(Color.GRAY); 		// in the color gray
					searchBar.setCaretPosition(0); 				// set the cursor to the front of the string
					searchBarHelper = false; 					//
				} 												//
				else if(searchBar.getForeground() == Color.GRAY)	// if you click the search bar while the 
				{												// prompt is displayed
					searchBar.setCaretPosition(0);				// set the cursor to the front of the string
				}												//
			} 													//
		}); 														//
		searchBarHelper = false; 								//
		searchBar.addKeyListener(new KeyAdapter() { 				//
			public void keyPressed(KeyEvent key) { 				// on key-down
				if (!searchBarHelper) { 							// if a user begins input of a query
					searchBar.setText(""); 						// remove the prompt from the search bar
					searchBar.setForeground(Color.BLACK); 		// change the text color to black
					searchBarHelper = true; 						//
				} else if (key.getKeyCode() == KeyEvent.VK_ENTER) { // if the pressed key is 'ENTER'
					btnSearch.doClick(); 						// virtually click the search button
				} 												//
			} 													//
			public void keyReleased(KeyEvent e) { 				// on key-up
				if (searchBar.getText().equals("")) {			// if there is no text in the search bar
					searchBar.setText("Enter Your Query"); 		// display prompt
					searchBar.setForeground(Color.GRAY);			// in the color gray
					searchBar.setCaretPosition(0); 				// set the cursor to the front of the string
					searchBarHelper = false; 					//
				} 												//
			} 													//
		}); 														//
		searchBar.setForeground(Color.GRAY); 					// set the initial text color to gray
		searchBar.setBounds(10, 11, 499, 29); 					//
		frame.getContentPane().add(searchBar); 					// add the search bar to the main frame
		searchBar.setColumns(10); 								//

		final JFileChooser inputDir = new JFileChooser();
		inputPath = null;
		Boolean badSelection = false;
		idToDoc = new HashMap<Integer, File>();
		docToId = new HashMap<String, Integer>();
		zoneTotalWords = new ArrayList<HashMap<Integer, Integer>>();

		inputDir.setCurrentDirectory(new java.io.File(".")); 				// GUI prompt of for user input directory
		inputDir.setDialogTitle("Please Select The Input Directory");
		inputDir.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		do {
			badSelection = false;
			int selection = inputDir.showOpenDialog(null); 	// prompt user for input directory
															// NOTE: the file chooser should appear in the current
															// system's look and feel
			if (selection == JFileChooser.APPROVE_OPTION) { 	// if the user clicks 'OK' in the file chooser
				try {
					inputPath = inputDir.getSelectedFile().getAbsolutePath();	// get the user's selection
				} catch (NullPointerException npe) { 		// if the user didn't select a directory
					badSelection = true;
				}
			} else { 										// if cancel or 'X' is clicked
				int option = JOptionPane.showConfirmDialog(null, "Would you like to Exit?", "Exit?", 0,
						JOptionPane.YES_NO_OPTION, null); 	// prompt for verification
				if (option == JOptionPane.YES_OPTION) { 		// on confirmation, if yes
					System.exit(0);
				} else { 									// if no
					badSelection = true;
				}
			}
		} while (badSelection);
		
		boolean checkForIndex = false;														// on user selection of a directory path, the system will check for an existing index.
																							// if found, the index will be loaded. If not a new index will be built
		File indexDir = new File("./index");													//will hold all indexes and the path reference file
		
		if(indexDir.exists())																//determine if the index directory exists
			checkForIndex = true;
		
		indexDir.mkdir();																	// make the dir (no action will take place if the dir already exists)
		
		if(checkForIndex && indexDir.list().length > 0)										//if there are files in the index dir
		{
			
			File indexPaths = new File("./index/paths.txt");									// this file will hold the path references 
			if(indexPaths.exists())															//  if the file already exists, then one or more indexes exists and may need to be loaded
			{
				Scanner fileRead = null;
				try {
					fileRead = new Scanner(indexPaths);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
				
				boolean loadMessageShown = false;
				
				while(fileRead.hasNext()){													//compare each reference and check if it matches the user directory path, if so load the indexes
					String type = fileRead.nextLine();
					String workingDir = fileRead.nextLine();
					String indexPath = fileRead.nextLine();
					
					if(type.equals("1") && workingDir.equals(inputPath))						//check for primary index of type 1 with matching directory path
					{
						if(!loadMessageShown)
						{
							loadMessageShown = true;
							Thread showMessage = new Thread(new Runnable() { 	
								public void run() {	
									JOptionPane.showMessageDialog(null, "Attempting To Load Index, Please Wait");		// on a good match, notify the user that the index will be loaded 
								}																					//Note: this is done in a thread to prevent the program from halting
							});
							showMessage.start();
						}
						
						FileInputStream fis;
						
						try {																// attempt to de-serialize the index
							System.out.println("Attempting index load of -> type: " + type + ", workingDir: " + workingDir + ", indexPath: " + indexPath);
							indexUpdate.acquire();
							fis = new FileInputStream(indexPath);
							ObjectInputStream ois = new ObjectInputStream(fis);
			    				dictionary = (WordDictionary) ois.readObject(); 
			    				ois.close();		
			    				idToDoc = dictionary.getIdToDoc();								//get all relevant data from the main index 
							docToId = dictionary.getDocToId();								//
							zoneTotalWords = dictionary.getZoneTotalWords();					//
							System.out.println("Success");
						} catch (IOException | ClassNotFoundException | InterruptedException e1) {
							//on failure, 'dictionary' will remain null and will be handled later
						}
						indexUpdate.release();
					}
					if(type.equals("2") && workingDir.equals(inputPath))						//check for secondary index of type 2 with matching directory path
					{
						FileInputStream fis;
						try {																//attempt to de-serialize the index
							System.out.println("Attempting index load of -> type: " + type + ", workingDir: " + workingDir + ", indexPath: " + indexPath);
							fis = new FileInputStream(indexPath);
							ObjectInputStream ois = new ObjectInputStream(fis);
			    				biWordDict = (WordDictionary) ois.readObject(); 
			    				ois.close();
			    				System.out.println("Success");
			    				break;
						} catch (IOException | ClassNotFoundException e1) {
							//on failure, 'dictionary' will remain null and will be handled later
						}						
					}
				}			
			}
			if(dictionary == null || biWordDict == null)							//if loading failed, build a new index
			{
				buildIndex(getAllSubFiles(inputPath, true), inputPath, true);
			}
			else																	//otherwise loading was successful, display the interface
			{
				frame.setTitle("Search Pro: Currently Searching In -> " + new File(inputPath).getName());
				frame.setVisible(true);
				System.out.println("Indexes Loaded.");
			}
		}
		else {																	// if no index dir or no files in index dir, build a new index
			buildIndex(getAllSubFiles(inputPath, true), inputPath, true);
		}
		
		Thread backGroundUpdate = new Thread(new Runnable() { 					// Will continuously check for updated to the index directory and will update the index as need with new files
			public void run() { 
				while(true) {
					updateIndex();
					
					try {
						Thread.sleep(30000);										//wait 30 seconds between checks
					} catch (InterruptedException e) {}
				}
			} 
		});
		backGroundUpdate.run();
		
		//ScheduledExecutorService updater = Executors.newScheduledThreadPool(1);  	//alternative to above solution, kept for reference 
		//updater.scheduleAtFixedRate(updateIndex, 0, 5, TimeUnit.SECONDS);
		
	}
	
	public ArrayList<Integer> rankedResults(String query, ArrayList<DocumentInfo> results){		//ranks and returns the top 5 scoring documents using TF-IDF and Weighted zone scoring
		
		if(results.isEmpty())									//if there where no results originally 
			return null;
		
		int resultCt = 0;
		double rankScore = 0;
		HashMap <Double, Integer> documentScores = new HashMap <Double, Integer>();
		ArrayList <Double> docScores = new ArrayList <Double>();
		
		for(DocumentInfo result: results)						//Calculate the score for each DocumentInfo using its data
		{			
			for(int i = 0; i < 3; i++)							// for each zone in the DocumentInfo 
			{
				try {
					int totalWords = zoneTotalWords.get(i).get(result.getID());	
					if(totalWords > 0)
					{
						double termOccuranceFraction = (double) result.getOccurrences(i) / totalWords;
						rankScore += Math.log(1 + termOccuranceFraction) * result.getWeight(i); //TF per Weighted Zone
					}
				}catch(NullPointerException zoneError) {}
			}
			double IDF = 0;
			
			if(query.contains(" "))
				IDF = Math.log((dictionary.totalNumOfDocs() / biWordDict.appearsInDocCt(query)));		//calculate IDF for multi word query
			else
				IDF = Math.log((dictionary.totalNumOfDocs() / dictionary.appearsInDocCt(query)));		//calculate IDF for single word query
			rankScore = rankScore * IDF; 															//applies IDF to score
			
			documentScores.put(rankScore, result.getID());		//store all document scores and references
			docScores.add(rankScore);							//store just the scores
			rankScore = 0;
		}
		
		Collections.sort(docScores);								//sort just the scores descending  
		ArrayList <Integer> rankedDocs = new ArrayList<Integer>();
		
		int i = 0;
		while(i < results.size())				// add the documents to the ranked results
		{	
			if(!rankedDocs.contains(documentScores.get(docScores.get(docScores.size()-i-1))) && docScores.get(docScores.size() - i -1) > 0.0) //check the result is not a duplicate
			{																		//note:	   ^^the above code ^^ ensures that no results are returned if the query term is a stop word (meaning all document scores will be 0.0)
				rankedDocs.add(documentScores.get(docScores.get(docScores.size()-i-1)));
				resultCt++;	
			}
			i++;
		}
		
		return rankedDocs;
	}
	
	public void alert(String text, JFrame parent, Color color) { // used to display alerts to the user, params text - the text of the alert | parent the JFrame the alert will be centered on top of
		Thread displayAlert = new Thread(new Runnable() { 		// each alert runs in its own thread to prevent the GUI from locking during alert execution
			public void run() {
				try {
					alertManager.acquire(); 						// request access to run (prevents concurrent alert threads, forcing alerts to be displayed in the order called)
				} catch (InterruptedException e1) {
					System.err.println("alert thread failed for: " + text);
				}

				System.out.println("alert thread starting for: " + text);

				JFrame alert = new JFrame(); 									// created a alert frame
				alert.setUndecorated(true); 										// without the system title bar
				alert.setResizable(false); 										// restrict its size
				alert.setLocationRelativeTo(parent); 							// position based on parent
				alert.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 			// ensures exiting of this frame does not end the program
				alert.getContentPane().setLayout(new BorderLayout(0, 0));			//
																				//
				JLabel alertText = new JLabel("  " + text + "  ");				// create a label with the param text
				alertText.setHorizontalAlignment(SwingConstants.CENTER); 			// centered (X,Y)
				alertText.setFont(new Font("Lucida Grande", Font.BOLD, 18)); 		// set the font and size
				alert.getContentPane().add(alertText); 							// add the label to the alert frame
																				//
				alertText.setVisible(true);										//
				alert.pack(); 													// set the size of the alert frame to the size of the added label
				alert.getContentPane().setBackground(color);						//								
				alert.setLocation(parent.getX() + 								//
						parent.getWidth() / 2 - alert.getWidth() / 2,			//
						parent.getY() - alert.getHeight()); 						// centered above the parent
				alert.setVisible(true); 											// display the alert frame
				parent.setVisible(true); 										// although the main frame is already visible, calling again moves the focus back to the main frame
				
				try {
					Thread.sleep(3000);											// display the alert for 3 seconds
				} catch (InterruptedException e) {
					System.err.println("alert thread failed for: " + text);
				}

				alert.setVisible(false);											// hide
				alert = null; 													// and dereference the alert frame
				alertManager.release();											// release, allowing the next thread to execute
			}
		});
		displayAlert.start();													// run the alert thread
	}
	
	public void buildIndex(File[] inputFiles, String dirPath, boolean needNewDictionary) {							//builds inverted indexes from each file, then serializes both indexes for later use and saves a lookup file for ease of loading on next start up
		System.out.println("Building Index of " + inputFiles.length + " files.");
		try {												//attempt to acquire access to the index 
			indexUpdate.acquire();
		} catch (InterruptedException e1) {}	
		
		Scanner fileReader = null; 
		int currZoneWordCt = 0;
		int currDocCt = 0;
		int docID = 0;
		double weight = 0.5;		// initial weight, for zone 1
		int zone = 0;
		boolean isFirstLine = true;
		boolean firstSentence = true;
		int percentHelper = 0;
		double percentInterval = (inputFiles.length / 100); 	// determine at what interval the progress bar should be incremented by calculating what 1% of the file count
															// (+1 prevents a divide by zero error if the directory has fewer than 100 files)
		File workingDir = new File(dirPath);
		
		if(needNewDictionary)
		{
			dictionary = new WordDictionary(dirPath, inputFiles, 1); 	// primary word index
			biWordDict = new WordDictionary(dirPath, inputFiles, 2); 	// bi-word index
		}
		else 												// this indicated an existing index will be updated 
		{			
			docID = dictionary.getDirectoryFiles().length;
			File[] newDirFiles = new File[dictionary.getDirectoryFiles().length + inputFiles.length];		//create an array large enough to hold all current files and new files
			
			int i = 0;
			for(i = 0; i < dictionary.getDirectoryFiles().length; i++)		//add all existing files to new array
			{
				newDirFiles[i] = dictionary.getDirectoryFiles()[i];
			}
			
			for(int j = 0; j < inputFiles.length; j++, i++)					//and then add all new files to the same new array
			{
				newDirFiles[i] = inputFiles[j];
			}
			dictionary.setDirectoryFiles(newDirFiles);						//store the new array as the WordDictionary's currently indexed files
		}
		JFrame progressFrame = new JFrame("Search Pro - Indexing -> " + workingDir.getName()); // create a new frame to hold the progress bar
		progressFrame.setSize(450, 45); 									//
		progressFrame.setResizable(false); 								// Restrict it's size
		progressFrame.setLocationRelativeTo(frame); 				
		progressFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 	//
		Container contentPane = progressFrame.getContentPane(); 			//
		JProgressBar progressBar = new JProgressBar(); 					// create a progress bar to display progress of indexing
		progressBar.setValue(0); 										// set initial value to 0
		progressBar.setStringPainted(true); 								//
		contentPane.add(progressBar, BorderLayout.NORTH); 				// add the progress bar element to the progress frame
		
		progressFrame.setVisible(true); 	
		
		if(frame.isVisible())
		{
			progressFrame.setLocation(frame.getX() + 					//
			frame.getWidth() / 2 - progressFrame.getWidth() / 2,			//
			progressFrame.getY() + frame.getHeight()/2 + progressFrame.getHeight()/2); 			
		}
										// display the frame
	
		fileReader = null;
	
		for (File currFile : inputFiles) { 	// for each file in the array
			currDocCt++;
			if (currDocCt == percentInterval * (percentHelper + 1)) { // if the currDocCt is an interval
				percentHelper++;
				progressBar.setValue(percentHelper); // increment the value displayed in the progress bar
			}
			
			if(currDocCt % 100 == 0) {
				System.out.println("Progress: " + currDocCt + "/" + inputFiles.length);
			}
			if (currFile != null && currFile.isFile() && !currFile.getAbsolutePath().toLowerCase().contains(".ds_store")) { // if the current path is a file
				
				docID++; // Increment the document ID
				
				idToDoc.put(docID, currFile); 			// create a <key, value> reference using the current document ID to reference the current file
				docToId.put(currFile.getName(), docID); // create a reverse <key, value> reference using the current file name to reference the current docID
				
				try {
					fileReader = new Scanner(currFile);
					String prevWord = null; // the previous word will be used to build the bi word index
	
					while (fileReader.hasNext()) {
												
						String currLine = fileReader.nextLine();
						String[] lineWords = currLine.split(" ");
						for(int i = 0; i < lineWords.length; i++)	//for each word in the current line
						{
							String currWord = lineWords[i].toLowerCase(); 						// normalize
							String checkWord = currWord.replaceAll("[^A-Za-z0-9 ']", " "); 		// filter characters
							
							if (currWord.equals(checkWord)) { 									// if the original word is the same as the filtered word
								if (!currWord.equals("")) { 										// and is not an empty string
									currZoneWordCt++;
									dictionary.addEntry(currWord, docID, weight, zone); 			//add an entry to the dictionary with the word, the document id the current zone's weight, and the current zone. The same will be done for the biWordDict
																								//
									if (prevWord != null) 										// then, if there is a previous word
										biWordDict.addEntry(prevWord + " " + currWord, docID, weight, zone); 	// add the previous word SPACE current word to the bi word index
									prevWord = currWord; 						// the current word becomes the previous word for the next iteration of the loop
								}
							} else { 														// if the original word is different from the filtered word
								String[] multipleWords = null;								//
								multipleWords = checkWord.split(" "); 						// split the string into multiple strings
	
								for (String word : multipleWords) { 							// do the same as above for each word
									if (!word.equals("")) {
										currZoneWordCt++;									//ct the words in each zone
										dictionary.addEntry(word, docID, weight, zone); 		//same as above
										if (prevWord != null)
											biWordDict.addEntry(prevWord + " " + word, docID, weight, zone);	//same as above
										prevWord = word;
									}
								}
							}
							if(currWord.contains(".") && firstSentence)						// if the current word is the last word in the first sentence, store all values for current zone 
							{																// and move to the 'body' zone
								HashMap <Integer, Integer> zoneAndWordCt = null;
								if(zoneTotalWords.size() < 3)
								{
									zoneAndWordCt = new HashMap <Integer, Integer>();
									zoneAndWordCt.put(docID, currZoneWordCt);
									zoneTotalWords.add(zoneAndWordCt);
								}
								else
								{
									zoneAndWordCt = zoneTotalWords.get(zone);
									zoneAndWordCt.put(docID, currZoneWordCt);
									zoneTotalWords.set(zone, zoneAndWordCt);
								}
															
								currZoneWordCt = 0;											//reset the ct for the next zone
								firstSentence = false;	
								zone++;														//increment zone number
								weight = 0.2;												//set the new zone's weight
							}
						}
						if(isFirstLine)														//if the current line is the first line, store all values for the current zone
						{																	// and move to the 'first sentence' zone
							HashMap <Integer, Integer> zoneAndWordCt = null;
							if(zoneTotalWords.size() < 3)
							{
								zoneAndWordCt = new HashMap <Integer, Integer>();
								zoneAndWordCt.put(docID, currZoneWordCt);
								zoneTotalWords.add(zoneAndWordCt);
							}
							else
							{
								zoneAndWordCt = zoneTotalWords.get(zone);
								zoneAndWordCt.put(docID, currZoneWordCt);
								zoneTotalWords.set(zone, zoneAndWordCt);
							}
							
							currZoneWordCt = 0;												//reset the ct for the next zone
							zone++;															//Increment the zone number
							weight = 0.3;													//set the new zone's weight
							isFirstLine = false;		
						}
					}
				} catch (FileNotFoundException e) {
					System.err.println("Failed to read: " + currFile.getAbsolutePath() + " - continuing"); // if the current file could not be accessed
					e.printStackTrace(); 
				}
			}
			HashMap <Integer, Integer> zoneAndWordCt = null;
			if(zoneTotalWords.size() < 3)													//if there are less than 3 zones in the current document add the current zone information to the next index in zoneTotalWords
			{
				zoneAndWordCt = new HashMap <Integer, Integer>();
				zoneAndWordCt.put(docID, currZoneWordCt);
				zoneTotalWords.add(zoneAndWordCt);
			}
			else																				//if there are 3 zones, store the body zone to the next index in zoneTotalWords	
			{
				zoneAndWordCt = zoneTotalWords.get(zone);
				zoneAndWordCt.put(docID, currZoneWordCt);
				zoneTotalWords.set(zone, zoneAndWordCt);
			}
			
			currZoneWordCt = 0;																//set all values back to defaults for the next file
			zone = 0;
			weight = 0.5;
			isFirstLine = true;
			firstSentence = true;
		}
		
		dictionary.storeRelevantData(idToDoc, docToId, zoneTotalWords);						//move relevant data to the dictionary for serialization
		progressBar.setValue(100);
						
		
		ObjectOutputStream oos;																//attempt to save both indexes and store there relative paths 
		try {			
			oos = new ObjectOutputStream(new FileOutputStream("./index/" + workingDir.getName() + "_t" + dictionary.getWhatType() + ".INDEX"));
			oos.writeObject(dictionary);
			oos.close();
			oos = new ObjectOutputStream(new FileOutputStream("./index/" + workingDir.getName() + "_t" + biWordDict.getWhatType() + ".INDEX"));
			oos.writeObject(biWordDict);
			oos.close();
			
			if(needNewDictionary) {
				PrintWriter indexPaths = new PrintWriter(new FileWriter("./index/paths.txt", true));
				indexPaths.println(dictionary.getWhatType() + "\n" + workingDir.getAbsolutePath() + "\n./index/" + workingDir.getName() + "_t" + dictionary.getWhatType() + ".INDEX");
				indexPaths.println(biWordDict.getWhatType() + "\n" + workingDir.getAbsolutePath() + "\n./index/" + workingDir.getName() + "_t" + biWordDict.getWhatType() + ".INDEX");
				indexPaths.close();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(!frame.isVisible())																//if the frame is currently not visible, display it
		{
			frame.setTitle("Search Pro: Currently Searching In -> " + new File(inputPath).getName());
			frame.setVisible(true);
		}
		
		progressFrame.setVisible(false); 													// once indexing is complete, hide
		progressFrame = null; 																// and dereference the progress bar frame
		
		if(!needNewDictionary)																//if the index was updated, notify the user of completion 
			alert("The Index Is Up To Date", frame, Color.ORANGE);
		
		indexUpdate.release();
	}

	public void updateIndex(){		//is called periodically to check for new files in the index directory path. If so, determines new files and called buildIndex passing new files
		try {
			indexUpdate.acquire();										//gain access to the index once available 
		} catch (InterruptedException e) {}
		
		File[] currDirFilesCheck = getAllSubFiles(inputPath, false);
		File[] currIndexFiles = dictionary.getDirectoryFiles();
				
		indexUpdate.release();
		
		if(currDirFilesCheck.length > currIndexFiles.length)				//compare the number of files in the directory to the number currently indexed, if different an update may be needed
		{	
			try {
				Thread.sleep(15000);										// wait to ensure all files from a file transfer are competed before updating index
			} catch (InterruptedException e) {
				//will not affect functionality, but may cause annoyance to user with back to back index updates
			}
			
			File[] currDirFiles = getAllSubFiles(inputPath, false); 		// get the current files in the system after waiting
			
			Thread doUpdate = new Thread(new Runnable() { 				//update in background
				public void run() {
					alert("New Files Added - The Index Will Now Update", frame, Color.ORANGE);					//notify the user
					File[] newFiles = new File[Math.abs(currDirFiles.length - currIndexFiles.length)];;			//created an index the size of the difference between the currently indexed files and the current directory files
					int i= 0;
					boolean found = false;
				
					for(File dirFile: currDirFiles)						//compare all files to determine the newly added files and add them to the array 
					{
						for(File indexFile: currIndexFiles)
						{
							if(dirFile != null && dirFile.getAbsolutePath().equals(indexFile.getAbsolutePath()))
							{
								found = true;
								break;
							}
						}
						if(!found && dirFile != null)
						{
							System.out.println("\tAdding: " + dirFile.getAbsolutePath());
							newFiles[i] = dirFile;
							i++;
						}
						found = false;
						if(newFiles.length != 0 && newFiles.length > (currDirFiles.length - currIndexFiles.length)) //once the number of new files is equal to the difference, exit the loop
							break;
					}
					buildIndex(newFiles, inputPath, false);				 //build the index, passing the new files and indicating a new dictionary is not needed
				}
			});
			doUpdate.start();
		}
	}
	
	public File[] getAllSubFiles(String rootPath, boolean showText) {
		ArrayList<File> allFiles = new ArrayList<File>();
		
		ArrayList<File> dirs = getAllSubDirs(rootPath, showText);
		System.out.println("Collecting All Files From " + dirs.size() + " Directories.");
		
		for(File dir : dirs) {
			File[] currFiles = new File(dir.getAbsolutePath()).listFiles();
			allFiles.addAll(Arrays.asList(currFiles));
		}
		
		return allFiles.toArray(new File [allFiles.size()]);
	}
	
	public ArrayList<File> getAllSubDirs(String rootPath, boolean showText) {
		if(showText)
			System.out.println("Searching For Sub Directories in: " + rootPath);
		
		ArrayList<File> allDirs = new ArrayList<File>();
		allDirs.add(new File(rootPath));
		
		File[] dirs = new File(rootPath).listFiles(File::isDirectory);
		
		for(File dir : dirs) {
			try {
				allDirs.addAll(getAllSubDirs(dir.getAbsolutePath(), showText));
			}
			catch(Exception e) {
				System.out.println("Permission Denied");
			}
		}
		
		return allDirs;
	}
}
	
	

