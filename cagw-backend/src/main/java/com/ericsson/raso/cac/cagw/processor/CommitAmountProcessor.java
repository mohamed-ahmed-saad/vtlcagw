package com.ericsson.raso.cac.cagw.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class CommitAmountProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		System.out.println("Entered into CommitAmountProcessor");
		
	}

}
