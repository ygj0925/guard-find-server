package com.guardfind.server.accounting;
import com.fasterxml.jackson.databind.JsonNode;
public interface AccountingRecognitionGateway {
 JsonNode recognize(String text,AccountingReceipt receipt,String date);
 String transcribe(byte[] audio,String mime);
}
