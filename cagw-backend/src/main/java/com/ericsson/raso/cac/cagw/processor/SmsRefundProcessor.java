package com.ericsson.raso.cac.cagw.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import com.ericsson.raso.cac.cagw.dao.Archive;
import com.ericsson.raso.cac.cagw.dao.ConcurrencyControl;
import com.ericsson.raso.cac.cagw.dao.MoveTransactionToArchive;
import com.ericsson.raso.cac.cagw.dao.PersistenceException;
import com.ericsson.raso.cac.cagw.dao.Transaction;
import com.ericsson.raso.cac.cagw.dao.TransactionDao;
import com.ericsson.raso.cac.smpp.pdu.viettel.SmResultNotify;
import com.ericsson.raso.cac.smpp.pdu.viettel.SmResultNotifyResponse;
import com.ericsson.raso.cac.smpp.pdu.viettel.WinOperationResult;
import com.satnar.air.ucip.client.UcipException;
import com.satnar.air.ucip.client.command.UpdateBalanceAndDateCommand;
import com.satnar.air.ucip.client.request.DedicatedAccountUpdateInformation;
import com.satnar.air.ucip.client.request.UpdateBalanceAndDateRequest;
import com.satnar.air.ucip.client.response.UpdateBalanceAndDateResponse;
import com.satnar.common.LogService;
import com.satnar.smpp.CommandStatus;

public class SmsRefundProcessor implements Processor {
	
	private static final String TRANSACTION_TYPE = "SMS Refund";
	@Override
	public void process(Exchange exchange) throws Exception {
	    SmResultNotify smppRequest = null;
		SmResultNotifyResponse smppResponse = null;
	    try{
			smppRequest = (SmResultNotify) exchange.getIn().getBody();		    
			LogService.stackTraceLog.info("Request >> " + smppRequest.toString());
            
		    // successful delivery... nothing to do
		    if (smppRequest.getFinalState().getValue() == 0) {
		    	LogService.appLog.debug("SmsRefundProcessor-process:Message devliery Success. No need to refund!");
		        smppResponse = this.getSuccessSmppResponse(smppRequest);
		        LogService.stackTraceLog.info("Response >> " + smppResponse.toString());
		        exchange.getOut().setBody(smppResponse);
		        this.moveToArchive(smppRequest);
		        return;
		    }
		    
		    Transaction txn = null;
		    TransactionDao txnDao = null;
		    try {
		            // check for data to refund		
		    	txnDao = new TransactionDao();
		    	String destinationAddress = txnDao.getUpdatedDestinationNumber(smppRequest.getDestinationAddress().getString());//30-JUL-2015: For number normalization
		    	
		        txn = txnDao.fetchSmsCharging(smppRequest.getSmId().getString(), 
		            smppRequest.getSourceAddress().getString(), 
		            destinationAddress);
		    } catch (PersistenceException e) {
                smppResponse = this.getRefundFailedSmppResponse(smppRequest);
                LogService.stackTraceLog.info("Response >> " + smppResponse.toString());
                exchange.getOut().setBody(smppResponse);
                return;
	        }
		    
		    // no data, potentially nothing to refund (for e.g., free sms or wrong transactionid
		    if (txn == null || txn.getAccountId() == null || txn.getAccountId().equalsIgnoreCase("")) {
		    	LogService.appLog.debug("SmsRefundProcessor-process:Transaction not found. Will not need to refund");
		        smppResponse = this.getSuccessSmppResponse(smppRequest);
		        LogService.stackTraceLog.info("Response >> " + smppResponse.toString());
                exchange.getOut().setBody(smppResponse);
                return;
		    }
		    
		    LogService.appLog.info("Transaction from DB: " + txn.toString());
		    
		    // lets refund
		    UpdateBalanceAndDateRequest ubdRequest = new UpdateBalanceAndDateRequest();
		    ubdRequest.setSubscriberNumber(txn.getChargedParty());
		    ubdRequest.setSubscriberNumberNAI(1);
		    ubdRequest.setSiteId("1");
		    //ubdRequest.setNegotiatedCapabilities(805646916);
		    //ubdRequest.setTransactionCode(smppRequest.getDestinationAddress().getString ());
		    //01-AUG-2015: to apply number normalization for destination number
		    ubdRequest.setTransactionCode(txnDao.getUpdatedDestinationNumber(smppRequest.getDestinationAddress().getString()));
		    ubdRequest.setTransactionType(TRANSACTION_TYPE);
		    StringBuilder sbLog = new StringBuilder("");
		    sbLog.append("SubscriberNumber:");sbLog.append(ubdRequest.getSubscriberNumber());
		    
		    String[] accounts = txn.getAccountId().split("\\|");
            String[] amounts = txn.getAmount().split("\\|");
            String[] accountTypes = txn.getAccountType().split("\\|");
            LogService.appLog.debug(String.format("Lets test the splits. accounts size: %d, accounts(0): %s, amounts size: %d, amounts(0): %s, accountTypes size: %d, accountTypes(0): %s",
                    accounts.length, accounts[0], amounts.length, amounts[0], accountTypes.length, accountTypes[0]));
            
            List<DedicatedAccountUpdateInformation> dasToUpdate = new ArrayList<>();
		    for (int i = 0; i < accounts.length; i++) {
		        LogService.appLog.debug(String.format("Preparing DA with %d account: %s, amount: %s, type: %s", i, accounts[i], amounts[i], accountTypes[i]));
		        /*if (accounts[i].equals("0")) {
		            ubdRequest.setAdjustmentAmountRelative("-" + amounts[i]);
		            ubdRequest.setTransactionCurrency("VND");
		            LogService.appLog.debug("Updating Main Account with " + amounts[i]);
		        } else {*/
		        
		        //25-JUL-2015: As per CDR finalized parameters confirmation, MA will also be treated as DA - 1
	            DedicatedAccountUpdateInformation dauInfo = new DedicatedAccountUpdateInformation();
	            dauInfo.setDedicatedAccountID(Integer.parseInt(accounts[i]));
	            dauInfo.setDedicatedAccountUnitType(Integer.parseInt(accountTypes[i]));
	            dauInfo.setAdjustmentAmountRelative("-" + amounts[i]);
	            
	            //if(accountTypes[i].equals("1")){//Monetary DA
	            if(Integer.parseInt(accountTypes[i]) == 1){
	            	ubdRequest.setTransactionCurrency("VND");
	            }
	            dasToUpdate.add(dauInfo); 
                LogService.appLog.debug("Updating request with " + dauInfo.toString());
		        //}
		    }		
		    if (dasToUpdate.size()>0) {
		        ubdRequest.setDedicatedAccountUpdateInformation(dasToUpdate);
		        LogService.appLog.debug("Hav atleast 1 DA to add to the request.");
		    }
		    LogService.appLog.debug("SmsRefundProcessor-process:AIR request:" + ubdRequest.toString());
		    sbLog = null;
		    
		    boolean refundResult = false;
		    try {
		        UpdateBalanceAndDateCommand command = new UpdateBalanceAndDateCommand(ubdRequest);
		        UpdateBalanceAndDateResponse ubdResponse = command.execute();
		        
		        LogService.stackTraceLog.debug("SmsRefundProcessor-process:AIR ResponseCode:"+ubdResponse.getResponseCode());
		        refundResult = true;
		        smppResponse = this.getSuccessSmppResponse(smppRequest);
		        LogService.stackTraceLog.info("Response >> " + smppResponse.toString());
		        exchange.getOut().setBody(smppResponse);
		    } catch (UcipException e) {
		    	LogService.appLog.debug("SmsRefundProcessor-process:Failed to refund !!",e);
		        smppResponse = this.getRefundFailedSmppResponse(smppRequest);
                LogService.stackTraceLog.info("Response >> " + smppResponse.toString());
		        exchange.getOut().setBody(smppResponse);
		        return;
 	        }		    
		    //now delete txn and move to archive now
		    this.moveToArchive(txn, smppRequest.getFinalState().getValue(), refundResult, System.currentTimeMillis());
		    LogService.appLog.info("Transaction archived for : " + smppRequest.getSmId().getString());
		}catch(Exception genE){//Added for debugging
			LogService.appLog.debug("SmsRefundProcessor-process:Encountered exception",genE);
            smppResponse = this.getRefundFailedSmppResponse(smppRequest);
            LogService.stackTraceLog.info("Response >> " + smppResponse.toString());
            exchange.getOut().setBody(smppResponse);
            return;
		}
	}

    private void moveToArchive(Transaction txn, int deliveryResult, boolean refundResult, long sysTime) {
        Archive archive = new Archive(txn);
        archive.setDeliveryStatus(deliveryResult == 0);
        archive.setRefundStatus(refundResult);
        archive.setRefundTime(sysTime);
        ConcurrencyControl.enqueueExecution(new MoveTransactionToArchive(txn, archive));
        
    }

    private void moveToArchive(SmResultNotify smppRequest) {
        Transaction transaction = null;
        try {
            // check for data to refund         
            transaction = new TransactionDao().fetchSmsCharging(smppRequest.getSmId().getString(), 
                    smppRequest.getSourceAddress().getString(), 
                    smppRequest.getDestinationAddress().getString());
        } catch (PersistenceException e) {                    	
            LogService.stackTraceLog.debug("SmsRefundProcessor-moveToArchive: Failed:SmId:"+smppRequest.getSmId().getValue(),e);
        }
        
        if (transaction != null) {
            Archive archive = new Archive(transaction);
            archive.setDeliveryStatus(smppRequest.getFinalState().getValue() == 0);
            archive.setRefundStatus(false);
            archive.setRefundTime(System.currentTimeMillis());
            ConcurrencyControl.enqueueExecution(new MoveTransactionToArchive(transaction, archive));
        }
    }

    private SmResultNotifyResponse getSuccessSmppResponse(SmResultNotify smppRequest) {
        SmResultNotifyResponse smppResponse = new SmResultNotifyResponse();
        smppResponse.setCommandSequence(smppRequest.getCommandSequence());
        smppResponse.setOperationResult(WinOperationResult.SUCCESS);
        return smppResponse;
    }

    private SmResultNotifyResponse getRefundFailedSmppResponse(SmResultNotify smppRequest) {
        SmResultNotifyResponse smppResponse = new SmResultNotifyResponse();
        smppResponse.setCommandSequence(smppRequest.getCommandSequence());
        smppResponse.setOperationResult(WinOperationResult.OTHER_ERRORS);
        return smppResponse;
    }

}
