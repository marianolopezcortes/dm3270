package com.bytezone.dm3270.filetransfer;

import java.util.ArrayList;
import java.util.List;

// CUT - Control Unit Terminal --------- Buffered
// DFT - Distributed Function Terminal - WSF
// http://publibz.boulder.ibm.com/cgi-bin/bookmgr/BOOKS/cn7a7003/2.4.1

public class Transfer
{
  private static int INBOUND_MAX_BUFFER_SIZE = 2048;

  private TransferContents transferContents;
  private TransferType transferType;
  private IndFileCommand indFileCommand;

  List<FileTransferOutboundSF> outboundRecords = new ArrayList<> ();
  List<DataRecord> dataRecords = new ArrayList<> ();
  int dataLength;

  byte[] inboundBuffer;
  int inboundBufferPtr;

  public enum TransferContents
  {
    MSG, DATA
  }

  public enum TransferType
  {
    DOWNLOAD,       //    mainframe -> terminal (send)
    UPLOAD          //    terminal -> mainframe (receive)
  }

  public Transfer (FileTransferOutboundSF outboundRecord)
  {
    add (outboundRecord);
  }

  // called from TransferManager.getTransfer()
  // called from TransferManager.closeTransfer()
  public void add (FileTransferOutboundSF outboundRecord)
  {
    outboundRecords.add (outboundRecord);

    if (transferContents == null)
      transferContents = outboundRecord.transferContents;   // MSG or DATA
    if (transferType == null)
      transferType = outboundRecord.transferType;           // DOWNLOAD or UPLOAD
  }

  // called from FileTransferOutboundSF.processReceive() - upload
  int add (DataRecord dataRecord)
  {
    if (dataRecords.contains (dataRecord))
      return dataRecords.indexOf (dataRecord) + 1;

    dataRecords.add (dataRecord);
    dataLength += dataRecord.getBufferLength ();

    return dataRecords.size ();
  }

  public boolean isDownloadData ()
  {
    return transferContents == TransferContents.DATA
        && transferType == TransferType.DOWNLOAD;
  }

  // called from TransferManager.closeTransfer()
  public byte[] combineDataBuffers ()
  {
    byte[] fullBuffer = new byte[dataLength];

    int ptr = 0;
    for (DataRecord dataRecord : dataRecords)
    {
      ptr = dataRecord.packBuffer (fullBuffer, ptr);
      //      if (ascii)
      //        dataRecord.checkAscii (crlf);
    }

    return fullBuffer;
  }

  // called from FileTransferOutboundSF.processOpen() - upload
  void setTransferBuffer (byte[] buffer)
  {
    inboundBuffer = buffer;
    inboundBufferPtr = 0;
  }

  DataRecord getDataHeader ()
  {
    assert hasMoreData ();

    int buflen = Math.min (INBOUND_MAX_BUFFER_SIZE, getBytesLeft ());
    DataRecord dataHeader =
        new DataRecord (inboundBuffer, inboundBufferPtr, buflen, false);
    inboundBufferPtr += buflen;
    add (dataHeader);

    return dataHeader;
  }

  int size ()
  {
    return dataRecords.size ();
  }

  public TransferContents getTransferContents ()
  {
    return transferContents;
  }

  public TransferType getTransferType ()
  {
    return transferType;
  }

  boolean cancelled ()
  {
    return false;
  }

  boolean hasMoreData ()
  {
    return getBytesLeft () > 0;
  }

  int getBytesLeft ()
  {
    if (inboundBuffer == null)
      return 0;
    return inboundBuffer.length - inboundBufferPtr;
  }

  public void setTransferCommand (IndFileCommand indFileCommand)
  {
    this.indFileCommand = indFileCommand;
  }

  public String getFileName ()
  {
    return indFileCommand.getFileName ();
  }

  public boolean hasTLQ ()
  {
    return indFileCommand.hasTLQ ();
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (String.format ("Contents ....... %s%n", transferContents));
    text.append (String.format ("Type ........... %s", transferType));

    int bufno = 0;
    for (DataRecord dataRecord : dataRecords)
      text.append (String.format ("%n  Buffer %3d ...  %,8d", bufno++,
                                  dataRecord.getBufferLength ()));

    text.append (String.format ("%nLength ......... %,9d", dataLength));
    text.append (String.format ("%ninbuf length ... %d",
                                inboundBuffer == null ? -1 : inboundBuffer.length));
    text.append (String.format ("%nin ptr ......... %d", inboundBufferPtr));

    return text.toString ();
  }
}