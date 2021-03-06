package com.satnar.smpp.transport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import com.satnar.common.LogService;
import com.satnar.smpp.StackMap;
import com.satnar.smpp.client.Esme;
import com.satnar.smpp.client.EsmeHelper;
import com.satnar.smpp.codec.SmppCodecException;
import com.satnar.smpp.pdu.SmppPdu;

public class WriteHelper { 
    
    private int lazyWritePeriod = 0;
    private Timer lazyWriteSchedule = null;
    private LazyWriteBuffer lazyWriteBuffer = null;
    
    private Connection smppConnection = null;
    
    public WriteHelper(Connection connection) {
        this.smppConnection = connection;
        this.lazyWriteBuffer = new LazyWriteBuffer(this.smppConnection.getEsmeLabel());
        lazyWritePeriod = this.smppConnection.getLazyWriteWait();
        this.lazyWriteSchedule = new Timer("LazyWriter-" + this.smppConnection.getEsmeLabel());
        LogService.appLog.info("Lazy Writer Scheduled with Frequency: " + this.lazyWritePeriod);
        this.lazyWriteSchedule.schedule(new LazyWriterTask(this.smppConnection, this.lazyWriteBuffer), this.lazyWritePeriod, this.lazyWritePeriod);
    }
    
    public synchronized void writeImmediate(SmppPdu payload) throws SmppTransportException  {
        if (this.smppConnection.getConnectionState() == SmppSessionState.CLOSED ||
                this.smppConnection.getConnectionState() == SmppSessionState.UNBOUND)
            throw new SmppTransportException(this.smppConnection.getEsmeLabel() + " - SMPP Session closed or Socket is broken. Reinitialize ESME now!!");
        
        try {
            ByteBuffer writeBuffer = this.smppConnection.getSendBuffer();
            byte[] serialized = payload.encode();
            writeBuffer.put(serialized);
            
            LogService.stackTraceLog.info(this.smppConnection.getEsmeLabel() + " - transmitting payload: " + EsmeHelper.prettyPrint(serialized));
            this.transmitNow();
            
            LogService.appLog.info(this.smppConnection.getEsmeLabel() + " - WriteHelper-writeImmediate:Done. Command Id:"+payload.getCommandId().name()+":Command Sequence:"+payload.getCommandSequence().getValue());
        } catch (SmppCodecException e) {
            LogService.appLog.warn(this.smppConnection.getEsmeLabel() + " - WriteHelper-writeImmediate: Encoding failed. Command Id:"+payload.getCommandId().name()+":Command Sequence:"+payload.getCommandSequence().getValue(),e);
        }
    }
    
    public synchronized void writeLazy(SmppPdu payload) throws SmppTransportException  {
        if (this.smppConnection.getConnectionState() == SmppSessionState.CLOSED ||
                this.smppConnection.getConnectionState() == SmppSessionState.UNBOUND)
            throw new SmppTransportException(this.smppConnection.getEsmeLabel() + " - SMPP Session closed or Socket is broken. Reinitialize ESME now!!");
        
        try {
            byte[] serialized = payload.encode();
            LogService.stackTraceLog.info(this.smppConnection.getEsmeLabel() + " - buffering payload: " + EsmeHelper.prettyPrint(serialized));
            
            if (this.lazyWriteBuffer.willOverflow(serialized.length)) {
                LogService.appLog.info("Seems like will overflow. So first flushing out the transmission, before buffering!!");
                this.flushTransmission();
            }    
            
            this.lazyWriteBuffer.write(serialized);
            if (this.lazyWriteBuffer.readyToTransmit()) {
                LogService.appLog.info("LazyBuffer ready for transmission, flushing now...");
                this.flushTransmission();
            }
            LogService.appLog.debug(this.smppConnection.getEsmeLabel() + " - WriteHelper-writeLazy:Done. Command Id:"+payload.getCommandId().name()+":Command Sequence:"+payload.getCommandSequence().getValue());
        } catch (SmppCodecException e) {
            LogService.appLog.warn(this.smppConnection.getEsmeLabel() + " - WriteHelper-writeLazy: Encoding failed. Command Id:"+payload.getCommandId().name()+":Command Sequence:"+payload.getCommandSequence().getValue(),e);
        }
    }
    
    private void flushTransmission() {
        try {
            ByteBuffer writeBuffer = this.smppConnection.getSendBuffer();
            writeBuffer.clear();
            writeBuffer.put(this.lazyWriteBuffer.flush());
            writeBuffer.flip();
            this.smppConnection.write(writeBuffer);
            writeBuffer.clear();
            LogService.appLog.debug(this.smppConnection.getEsmeLabel() + " - flushed transmission window");
            
        }   catch (SmppTransportException e) {
            if (e.getCause() != null && e.getCause() instanceof IOException) {
                LogService.appLog.error(this.smppConnection.getEsmeLabel() + " - WriteHelper-writeLazy:socket seems to be broken!!",e);
                Esme session = StackMap.getStack(this.smppConnection.getEsmeLabel());
                if (session != null)
                    session.stop();
            }
        }
    }
    
    private void transmitNow() {
        try {
            ByteBuffer writeBuffer = this.smppConnection.getSendBuffer();
            writeBuffer.flip();
            this.smppConnection.write(writeBuffer);
            writeBuffer.clear();
            LogService.appLog.debug(this.smppConnection.getEsmeLabel() + " - flushed transmission window");
            
        }   catch (SmppTransportException e) {
            if (e.getCause() != null && e.getCause() instanceof IOException) {
                LogService.appLog.error(this.smppConnection.getEsmeLabel() + " - WriteHelper-writeLazy:socket seems to be broken!!",e);
                Esme session = StackMap.getStack(this.smppConnection.getEsmeLabel());
                if (session != null)
                    session.stop();
            }
        }
    }
    

    public void stop() {
        this.lazyWriteSchedule.cancel();
        this.lazyWriteSchedule = null;
        
    }

    
    


    
    class LazyWriterTask extends TimerTask {
        
        private Connection connection = null;
        private LazyWriteBuffer lazyWriteBuffer = null;
        
        public LazyWriterTask(Connection connection, LazyWriteBuffer lazyWriteBuffer) {
            this.connection = connection;
            this.lazyWriteBuffer = lazyWriteBuffer;
        }

        @Override
        public void run() {
            try {
                if (this.connection.getConnectionState() == SmppSessionState.BOUND_RX ||
                        this.connection.getConnectionState() == SmppSessionState.BOUND_TX ||
                        this.connection.getConnectionState() == SmppSessionState.BOUND_TRX) {
                    
                    LogService.appLog.debug(String.format("Session: %s - LazyWriter check connection state: %s, valid for write!!", 
                            this.connection.getEsmeLabel(), this.connection.getConnectionState()));
                    if (this.lazyWriteBuffer.hasContent()) {
                        ByteBuffer writeBuffer = this.connection.getSendBuffer();
                        synchronized (writeBuffer) {
                            writeBuffer.clear();
                            writeBuffer.put(this.lazyWriteBuffer.flush());
                            writeBuffer.flip();
                            this.connection.write(writeBuffer);
                            writeBuffer.clear();
                            LogService.stackTraceLog.debug(this.connection.getEsmeLabel() + " - flushed transmission window");
                        }
                    } else {
                        LogService.appLog.debug(String.format("Session: %s - LazyWriter - nothing to flush", this.connection.getEsmeLabel()));
                    }
                }
                
            } catch (SmppTransportException e) {
                if (e.getCause() != null && e.getCause() instanceof IOException) {
                    LogService.stackTraceLog.debug(String.format("Session: %s - LazyWriter: socket seems to be broken.", this.connection.getEsmeLabel()));
                    Esme session = StackMap.getStack(this.connection.getEsmeLabel());
                    session.stop();
                }
            }
        }
    }






}
