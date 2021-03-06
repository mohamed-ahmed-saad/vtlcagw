package com.satnar.smpp.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.satnar.common.LogService;

public class SlidingWindowBuffer {
    
    private byte[] slidingWindow = null;
    private ByteArrayOutputStream incoming = null;
    private ByteArrayInputStream consuming = null;
    private DataInputStream parser = null;
    
    public synchronized void rewind(byte[] rewind) throws SmppTransportException {
        try {
            ByteArrayOutputStream baosRaw = new ByteArrayOutputStream();
            baosRaw.write(rewind);
            
            if (this.parser != null) {
                int burstWindow = this.parser.available();
                LogService.appLog.info("Previous Remaining Sliding Window size: " + burstWindow);
                if (burstWindow > 0) {
                    byte[] tempRemaining = new byte[burstWindow];
                    this.parser.read(tempRemaining);
                    baosRaw.write(tempRemaining);
                } 
            }
            
            this.slidingWindow = baosRaw.toByteArray();
            LogService.appLog.info("New Rewinded Sliding Window size: " + this.slidingWindow.length);
            baosRaw.close();
            baosRaw = null;
            
            if (this.parser != null) {
                this.parser.close();
                this.parser = null;
            }
            
            if (this.consuming != null) {
                this.consuming.close();
                this.consuming = null;
            }
            
            this.consuming = new ByteArrayInputStream(this.slidingWindow);
            this.parser = new DataInputStream(this.consuming);
            LogService.appLog.info("Sliding Window ready. Check buffer: " + this.parser.available());
        } catch (IOException e) {
            LogService.appLog.error("sliding window rewind failed. Check exception: " + e, e);
            throw new SmppTransportException("Cannot rewind. Size: " + rewind.length);
        }
    }
    
    public synchronized void push(byte[] payload) throws SmppTransportException {
        try {
            // check for previous sliding window remaining...
            if (parser != null) {
                int burstWindow = this.parser.available();
                LogService.appLog.info("Previous remaining window size: " + burstWindow);
                if (burstWindow > 0) {
                    this.slidingWindow = new byte[burstWindow];
                    this.parser.read(this.slidingWindow);
                } else {
                    this.slidingWindow = null;
                }
            }
            if (this.parser != null) {
                this.parser.close();
                this.parser = null;
            }
            if (this.consuming != null) {
                this.consuming.close();
                this.consuming = null;
            }
            
            this.incoming = new ByteArrayOutputStream();
            if (slidingWindow != null && slidingWindow.length > 0) {
                LogService.appLog.info("Existing Sliding Window: " + this.slidingWindow.length + ", Incoming Window: " + payload.length);
                this.incoming.write(this.slidingWindow);                
                this.incoming.write(payload);
            } else {
                LogService.appLog.info("No Existing Sliding Window: 0, Incoming Window: " + payload.length);
                this.incoming.write(payload);
            }
            byte[] consolidatedWindow = this.incoming.toByteArray();
            this.incoming.close();
            this.incoming = null;
            
            this.consuming = new ByteArrayInputStream(consolidatedWindow);
            this.parser = new DataInputStream(this.consuming);
            
            
        } catch (IOException e) {
            LogService.appLog.error("sliding window push failed. Check exception: " + e, e);
            throw new SmppTransportException("Cannot accept incoming stream. Size: " + payload.length);
        }
    }
    
    public DataInputStream getParser() {
        return this.parser;
    }

    
    
    
}
