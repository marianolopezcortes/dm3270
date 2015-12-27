package com.bytezone.dm3270.filetransfer;

import java.util.ArrayList;
import java.util.List;

// CUT - Control Unit Terminal --------- Buffered
// DFT - Distributed Function Terminal - WSF
// http://publibz.boulder.ibm.com/cgi-bin/bookmgr/BOOKS/cn7a7003/2.4.1

public class Transfer
{
  private static int INBOUND_MAX_BUFFER_SIZE = 2048;

  private TransferContents transferContents;          // MSG or DATA
  private TransferType transferType;                  // UPLOAD or DOWNLOAD
  private IndFileCommand indFileCommand;              // user's TSO command

  List<DataRecord> dataRecords = new ArrayList<> ();  // downloading data
  private int dataLength;

  private DataRecord message;

  byte[] inboundBuffer;       // uploading data
  int inboundBufferPtr;

  public enum TransferContents
  {
    MSG, DATA
  }

  public enum TransferType
  {
    DOWNLOAD,       // mainframe -> terminal (send)
    UPLOAD          // terminal -> mainframe (receive)
  }

  public Transfer (IndFileCommand indFileCommand)
  {
    this.indFileCommand = indFileCommand;
  }

  public void compare (IndFileCommand indFileCommand)
  {
    if (this.indFileCommand != null)
      this.indFileCommand.compareWith (indFileCommand);
  }

  public String getMessage ()
  {
    if (!isMessage () || message == null)
      return "";

    return message.getText ();
  }

  public boolean isMessage ()
  {
    return transferContents == TransferContents.MSG;
  }

  public boolean isData ()
  {
    return transferContents == TransferContents.DATA;
  }

  // called from TransferManager.getTransfer()
  // called from TransferManager.closeTransfer()
  public void add (FileTransferOutboundSF outboundRecord)
  {
    if (outboundRecord.transferContents != null)
      transferContents = outboundRecord.transferContents;   // MSG or DATA

    if (transferType == null)
      transferType = outboundRecord.transferType;           // UPLOAD or DOWNLOAD
  }

  // called from FileTransferOutboundSF.processDownload()
  int add (DataRecord dataRecord)
  {
    if (isMessage ())
    {
      message = dataRecord;
      return 1;
    }

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
    int length = dataLength;
    if (indFileCommand.ascii () && indFileCommand.crlf ())
      --length;       // assumes the file has 0x1A on the end
    byte[] fullBuffer = new byte[length];

    int ptr = 0;
    for (DataRecord dataRecord : dataRecords)
    {
      ptr = dataRecord.packBuffer (fullBuffer, ptr);

      // check for non-ascii characters
      if (indFileCommand.ascii ())
        dataRecord.checkAscii (indFileCommand.crlf ());
    }

    if (fullBuffer.length < dataLength)
    {
      assert fullBuffer[fullBuffer.length - 2] == 0x0D;
      assert fullBuffer[fullBuffer.length - 1] == 0x0A;
      assert fullBuffer.length == dataLength - 1;
      System.out.println ("successfully adjusted");
    }

    return fullBuffer;
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

  public int size ()
  {
    return dataRecords.size ();
  }

  public int getDataLength ()
  {
    return dataLength;      // used to display buffer length on the console
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
    inboundBuffer = indFileCommand == null ? null : indFileCommand.getBuffer ();
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
    text.append (String.format ("Type ........... %s%n", transferType));
    text.append (String.format ("Command ........ %s%n", indFileCommand.getCommand ()));

    if (isMessage ())
      text.append (String.format ("Message ........ %s%n", getMessage ()));
    else
    {
      int bufno = 0;
      for (DataRecord dataRecord : dataRecords)
        text.append (String.format ("  Buffer %3d ...  %,8d%n", bufno++,
                                    dataRecord.getBufferLength ()));

      text.append (String.format ("Length ......... %,9d%n", dataLength));
    }

    if (inboundBuffer != null)
    {
      text.append (String.format ("inbuf length ... %d%n",
                                  inboundBuffer == null ? -1 : inboundBuffer.length));
      text.append (String.format ("in ptr ......... %d%n", inboundBufferPtr));
    }

    if (text.length () > 0)
      text.deleteCharAt (text.length () - 1);

    return text.toString ();
  }
}