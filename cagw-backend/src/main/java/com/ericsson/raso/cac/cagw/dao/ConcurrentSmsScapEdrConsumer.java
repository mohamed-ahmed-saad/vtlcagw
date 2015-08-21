package com.ericsson.raso.cac.cagw.dao;

import java.awt.font.NumericShaper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConcurrentSmsScapEdrConsumer {
    
    private static final String COMMA = ",";
    private static final String PIPE = "|";
    private static final String ACCT_DELIM = "/";
    private static final String SEMI_COLON = ";";
    private static final String DA_PREFIX = "DA";
    
    private static List<SmsEdrPersistenceHelper> workers = null;
    private static int workerIndex = 0;
    
    public static void main(String[] args) {
        if (args.length < 4) {
           System.out.println("Usage: java SmsScapEdrConsumer <fully_qualified_path_to_input_file> <cassandra_ip_address_csv_list> <keyspace_name> <number_of_workers>");  
           System.exit(1);
        }
        
        int workerCount = 0;
        try {
            workerCount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.out.println("'number_of_workers' was not found positive integer!");
            System.exit(2);
        }
                
        processInputFile(args[0], args[1], args[2], workerCount);
    }

    private static void processInputFile(String csvPath, String cassandraIp, String keyspace, int numberOfWorkers) {
        
        workers = new ArrayList<SmsEdrPersistenceHelper>(numberOfWorkers);
        int poolSize = 800/numberOfWorkers;
        for (int i=0; i<numberOfWorkers; i++) {
            workers.add(new SmsEdrPersistenceHelper(cassandraIp, keyspace, poolSize));
        }
        System.out.println("Prepared Concurrent Cassandra Pool with: " + cassandraIp);
        
        String recordEntry = null;
        FileReader fileInput = null;
        BufferedReader textReader = null;
        int totalCount = 0;
        int successCount = 0;
        int failureCount = 0;
        
        try {
        	System.out.println("Processing started for file:"+csvPath);
            fileInput = new FileReader(csvPath);
            textReader = new BufferedReader(fileInput);            
            TransactionDao txnHelper = new TransactionDao();
            
            while(true) {            	
                recordEntry = textReader.readLine();
                if (recordEntry == null) {
                    System.out.println("No more entries... exiting..from:"+csvPath);
                    break;
                }
                
                // Handle record
                totalCount++;
                if (recordEntry.equals("") || recordEntry.contains(COMMA)) {
                    System.out.println("Skipping invalid entry at line: " + totalCount);
                    failureCount++;
                    continue;
                }
                
                
                String[] fields = recordEntry.split(COMMA);
                if (fields != null) {
                    SmsEdrPersistenceHelper worker = getWorker();
                    worker.submitFetchTransaction(fields);
                    
                    boolean keepLooping = true;
                    if (worker.anyFetchPending()) {
                        while (keepLooping) {
                            Transaction transaction = worker.fetchTransaction();
                            if (transaction != null) {
                                keepLooping = false;
                                worker = getWorker();
                                if (fields.length > 6) {
                                    worker.submitUpdateTransaction(transaction);
                                    if (worker.anyUpdatePending()) {
                                        Boolean updateResult = null;
                                        do {
                                            updateResult = worker.getUpdateResult();
                                        } while (updateResult == null);
                                        
                                        if (updateResult)
                                            successCount++;
                                        else
                                            failureCount++;
                                    }
                                } else {
                                    transaction.setChargeStatus(true);
                                    worker.submitDeleteTransaction(transaction);
                                    if (worker.anyDeletePending()) {
                                        Boolean deleteResult = null;
                                        do {
                                            deleteResult = worker.getDeleteResult();
                                        } while (deleteResult == null);
                                        
                                        if (deleteResult)
                                            successCount++;
                                        else
                                            failureCount++;
                                    }
                                }
                            }
                        }
                    }
	            }	            
            }
        } catch(Exception genE){
        	System.out.println("Encountered exception while processing file: " + genE.getMessage());
        	try {
                new File(csvPath + ".failed").createNewFile();
            } catch (IOException e1) {
            	System.out.println("Unable to create Failed File for:"+csvPath);
            }
        } finally{
        	System.out.println("Success Record Count: " + successCount); 
        	fileInput = null;
        	textReader = null;
        	System.exit(4);
        }
        System.out.println("\n\nExecution Summary");
        System.out.println("=================");
        System.out.println("Total Records processed: " + totalCount);
        System.out.println("Successfully processed:  " + successCount);
        System.out.println("Failure in processing:   " + failureCount);
    }
    
    private static SmsEdrPersistenceHelper getWorker() {
        if (workerIndex == workers.size())
            workerIndex = 0;
         return workers.get(workerIndex++);
    }
}
