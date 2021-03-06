/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.co.yahoo.dataplatform.mds.binary.maker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import java.util.List;
import java.util.ArrayList;

import jp.co.yahoo.dataplatform.mds.compressor.FindCompressor;
import jp.co.yahoo.dataplatform.mds.compressor.ICompressor;
import jp.co.yahoo.dataplatform.mds.spread.column.ICell;
import jp.co.yahoo.dataplatform.mds.spread.column.PrimitiveCell;
import jp.co.yahoo.dataplatform.mds.spread.column.IColumn;
import jp.co.yahoo.dataplatform.mds.spread.column.ColumnType;
import jp.co.yahoo.dataplatform.mds.spread.column.PrimitiveColumn;
import jp.co.yahoo.dataplatform.mds.spread.analyzer.IColumnAnalizeResult;
import jp.co.yahoo.dataplatform.mds.spread.analyzer.StringColumnAnalizeResult;

import jp.co.yahoo.dataplatform.schema.objects.StringObj;
import jp.co.yahoo.dataplatform.schema.objects.PrimitiveObject;

import jp.co.yahoo.dataplatform.mds.binary.ColumnBinary;
import jp.co.yahoo.dataplatform.mds.binary.ColumnBinaryMakerConfig;
import jp.co.yahoo.dataplatform.mds.binary.ColumnBinaryMakerCustomConfigNode;
import jp.co.yahoo.dataplatform.mds.binary.UTF8BytesLinkObj;
import jp.co.yahoo.dataplatform.mds.binary.maker.index.RangeStringIndex;
import jp.co.yahoo.dataplatform.mds.binary.maker.index.SequentialStringCellIndex;
import jp.co.yahoo.dataplatform.mds.blockindex.BlockIndexNode;
import jp.co.yahoo.dataplatform.mds.blockindex.StringRangeBlockIndex;
import jp.co.yahoo.dataplatform.mds.inmemory.IMemoryAllocator;

public class OptimizeDumpStringColumnBinaryMaker implements IColumnBinaryMaker{

  public static ColumnType getDiffColumnType( final int min , final int max ){
    int diff = max - min;
    if( diff < 0 ){
      return ColumnType.INTEGER;
    }

    if( diff <= Byte.MAX_VALUE ){
      return ColumnType.BYTE;
    }
    else if( diff <= Short.MAX_VALUE ){
      return ColumnType.SHORT;
    }

    return ColumnType.INTEGER;
  }

  public static ILengthMaker chooseLengthMaker( final boolean hasNull , final int min , final int max ){
    if( min == max && ! hasNull ){
      return new FixedLengthMaker( min );
    }

    ColumnType diffType = getDiffColumnType( min , max );
    if( max <= Byte.valueOf( Byte.MAX_VALUE ).intValue() ){
      return new ByteLengthMaker();
    }
    else if( diffType == ColumnType.BYTE ){
      return new DiffByteLengthMaker( min );
    }
    else if( max <= Short.valueOf( Short.MAX_VALUE ).intValue() ){
      return new ShortLengthMaker();
    }
    else if( diffType == ColumnType.SHORT ){
      return new DiffShortLengthMaker( min );
    }
    else{
      return new IntLengthMaker();
    }
  }

  public interface ILengthMaker{

    int calcBinarySize( final int columnSize );

    void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException;

    int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException;

  }

  public static class FixedLengthMaker implements ILengthMaker{

    private final int min;

    public FixedLengthMaker( final int min ){
      this.min = min;
    }

    @Override
    public int calcBinarySize( final int columnSize ){
      return 0;
    }

    @Override
    public void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException{
      return;
    }

    @Override
    public int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException{
      int[] result = new int[size];
      for( int i = 0 ; i < result.length ; i++ ){
        result[i] = min;
      }
      return result;
    }

  }

  public static class ByteLengthMaker implements ILengthMaker{

    @Override
    public int calcBinarySize( final int columnSize ){
      return Byte.BYTES * columnSize;
    }

    @Override
    public void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException{
      for( int i = 0 ; i < objArray.length ; i++ ){
        wrapBuffer.put( (byte)objArray[i].length );
      }
    }

    @Override
    public int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException{
      int[] result = new int[size];
      for( int i = 0 ; i < size ; i++ ){
        result[i] = wrapBuffer.get();
      }
      return result;
    }

  }

  public static class DiffByteLengthMaker implements ILengthMaker{

    private final int min;

    public DiffByteLengthMaker( final int min ){
      this.min = min;
    }

    @Override
    public int calcBinarySize( final int columnSize ){
      return Byte.BYTES * columnSize;
    }

    @Override
    public void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException{
      for( int i = 0 ; i < objArray.length ; i++ ){
        wrapBuffer.put( (byte)( objArray[i].length - min ) );
      }
    }

    @Override
    public int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException{
      int[] result = new int[size];
      for( int i = 0 ; i < size ; i++ ){
        result[i] = wrapBuffer.get() + min;
      }
      return result;
    }

  }

  public static class ShortLengthMaker implements ILengthMaker{

    @Override
    public int calcBinarySize( final int columnSize ){
      return Short.BYTES * columnSize;
    }

    @Override
    public void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException{
      for( int i = 0 ; i < objArray.length ; i++ ){
        wrapBuffer.putShort( (short)objArray[i].length );
      }
    }

    @Override
    public int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException{
      int[] result = new int[size];
      for( int i = 0 ; i < size ; i++ ){
        result[i] = wrapBuffer.getShort();
      }
      return result;
    }

  }

  public static class DiffShortLengthMaker implements ILengthMaker{

    private final int min;

    public DiffShortLengthMaker( final int min ){
      this.min = min;
    }

    @Override
    public int calcBinarySize( final int columnSize ){
      return Short.BYTES * columnSize;
    }

    @Override
    public void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException{
      for( int i = 0 ; i < objArray.length ; i++ ){
        wrapBuffer.putShort( (short)( objArray[i].length - min ) );
      }
    }

    @Override
    public int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException{
      int[] result = new int[size];
      for( int i = 0 ; i < size ; i++ ){
        result[i] = wrapBuffer.getShort() + min;
      }
      return result;
    }

  }

  public static class IntLengthMaker implements ILengthMaker{

    @Override
    public int calcBinarySize( final int columnSize ){
      return Integer.BYTES * columnSize;
    }

    @Override
    public void create( final byte[][] objArray , final ByteBuffer wrapBuffer ) throws IOException{
      for( int i = 0 ; i < objArray.length ; i++ ){
        wrapBuffer.putInt( objArray[i].length );
      }
    }

    @Override
    public int[] getLengthArray( final ByteBuffer wrapBuffer , final int size ) throws IOException{
      int[] result = new int[size];
      for( int i = 0 ; i < size ; i++ ){
        result[i] = wrapBuffer.getInt();
      }
      return result;
    }

  }

  @Override
  public ColumnBinary toBinary(final ColumnBinaryMakerConfig commonConfig , final ColumnBinaryMakerCustomConfigNode currentConfigNode , final IColumn column ) throws IOException{
    if( column.size() == 0 ){
      return new UnsupportedColumnBinaryMaker().toBinary( commonConfig , currentConfigNode , column );
    }

    ColumnBinaryMakerConfig currentConfig = commonConfig;
    if( currentConfigNode != null ){
      currentConfig = currentConfigNode.getCurrentConfig();
    }

    byte[] nullFlagBytes = new byte[column.size()];
    byte[][] objList = new byte[column.size()][];
    int totalLength = 0;
    int logicalDataLength = 0;
    boolean hasNull = false;
    String min = null;
    String max = "";
    int minLength = Integer.MAX_VALUE;
    int maxLength = 0;
    for( int i = 0 ; i < column.size() ; i++ ){
      ICell cell = column.get(i);
      if( cell.getType() == ColumnType.NULL ){
        nullFlagBytes[i] = (byte)1;
        objList[i] = new byte[0];
        hasNull = true;
        continue;
      }
      PrimitiveCell byteCell = (PrimitiveCell) cell;
      String strObj = byteCell.getRow().getString();
      if( strObj == null ){
        nullFlagBytes[i] = (byte)1;
        objList[i] = new byte[0];
        hasNull = true;
        continue;
      }
      byte[] obj = strObj.getBytes( "UTF-8" );
      if( maxLength < obj.length ){
        maxLength = obj.length;
      }
      if( obj.length < minLength ){
        minLength = obj.length;
      }
      totalLength += obj.length;
      logicalDataLength += Integer.BYTES + obj.length;
      objList[i] = obj;
      if( max.compareTo( strObj ) < 0 ){
        max = strObj;
      }
      if( min == null || 0 < min.compareTo( strObj ) ){
        min = strObj;
      }
    }

    if( ! hasNull && min.equals( max ) ){
      return ConstantColumnBinaryMaker.createColumnBinary( new StringObj( min ) , column.getColumnName() , column.size() );
    }

    int nullBinaryLength = nullFlagBytes.length;
    if( ! hasNull ){
      nullBinaryLength = 0;
    }
    ILengthMaker lengthMaker = chooseLengthMaker( hasNull , minLength , maxLength );
    int binaryLength = Integer.BYTES * 2 + Integer.BYTES + nullBinaryLength + lengthMaker.calcBinarySize( column.size() ) + totalLength;
    byte[] binaryRaw = new byte[binaryLength];
    ByteBuffer wrapBuffer = ByteBuffer.wrap( binaryRaw , 0 , binaryRaw.length );
    wrapBuffer.putInt( minLength );
    wrapBuffer.putInt( maxLength );
    if( hasNull ){
      wrapBuffer.putInt( 1 );
      wrapBuffer.put( nullFlagBytes );
    }
    else{
      wrapBuffer.putInt( 0 );
    }
    lengthMaker.create( objList , wrapBuffer );
    for( int i = 0 ; i < objList.length ; i++ ){
      wrapBuffer.put( objList[i] );
    }
    byte[] compressBinaryRaw = currentConfig.compressorClass.compress( binaryRaw , 0 , binaryRaw.length );

    int minCharLength = Character.BYTES * min.length();
    int maxCharLength = Character.BYTES * max.length();
    int headerSize = Integer.BYTES + minCharLength + Integer.BYTES + maxCharLength;

    byte[] binary = new byte[headerSize + compressBinaryRaw.length];
    ByteBuffer binaryWrapBuffer = ByteBuffer.wrap( binary );
    binaryWrapBuffer.putInt( minCharLength );
    binaryWrapBuffer.asCharBuffer().put( min );
    binaryWrapBuffer.position( binaryWrapBuffer.position() + minCharLength );
    binaryWrapBuffer.putInt( maxCharLength );
    binaryWrapBuffer.asCharBuffer().put( max );
    binaryWrapBuffer.position( binaryWrapBuffer.position() + maxCharLength );
    binaryWrapBuffer.put( compressBinaryRaw );

    return new ColumnBinary( this.getClass().getName() , currentConfig.compressorClass.getClass().getName() , column.getColumnName() , ColumnType.STRING , column.size() , binaryRaw.length , logicalDataLength , -1 , binary , 0 , binary.length , null );
  }

  @Override
  public int calcBinarySize( final IColumnAnalizeResult analizeResult ){
    StringColumnAnalizeResult stringAnalizeResult = (StringColumnAnalizeResult)analizeResult;
    boolean hasNull = analizeResult.getNullCount() != 0;
    if( ! hasNull && analizeResult.getUniqCount() == 1 ){
      return stringAnalizeResult.getUniqUtf8ByteSize();
    }
    int nullBinaryLength = stringAnalizeResult.getColumnSize();
    if( ! hasNull ){
      nullBinaryLength = 0;
    }
    ILengthMaker lengthMaker = chooseLengthMaker( hasNull , stringAnalizeResult.getMinUtf8Bytes() , stringAnalizeResult.getMaxUtf8Bytes() );
    return Integer.BYTES * 2 + Integer.BYTES + nullBinaryLength + lengthMaker.calcBinarySize( stringAnalizeResult.getColumnSize() ) + stringAnalizeResult.getTotalUtf8ByteSize();
  }

  @Override
  public IColumn toColumn( final ColumnBinary columnBinary ) throws IOException{
    ByteBuffer wrapBuffer = ByteBuffer.wrap( columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength
);
    int minLength = wrapBuffer.getInt();
    char[] minCharArray = new char[minLength / Character.BYTES];
    wrapBuffer.asCharBuffer().get( minCharArray );
    wrapBuffer.position( wrapBuffer.position() + minLength );

    int maxLength = wrapBuffer.getInt();
    char[] maxCharArray = new char[maxLength / Character.BYTES];
    wrapBuffer.asCharBuffer().get( maxCharArray );
    wrapBuffer.position( wrapBuffer.position() + maxLength );

    String min = new String( minCharArray );
    String max = new String( maxCharArray );

    int headerSize = Integer.BYTES + minLength + Integer.BYTES + maxLength;
    return new HeaderIndexLazyColumn(
      columnBinary.columnName ,
      columnBinary.columnType ,
      new StringColumnManager(
        columnBinary ,
        columnBinary.binaryStart + headerSize ,
        columnBinary.binaryLength - headerSize ) ,
      new RangeStringIndex( min , max )
    );
  }

  @Override
  public void loadInMemoryStorage( final ColumnBinary columnBinary , final IMemoryAllocator allocator ) throws IOException{
    ByteBuffer headerWrapBuffer = ByteBuffer.wrap( columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    int minCharLength = headerWrapBuffer.getInt();
    headerWrapBuffer.position( headerWrapBuffer.position() + minCharLength );

    int maxCharLength = headerWrapBuffer.getInt();
    headerWrapBuffer.position( headerWrapBuffer.position() + maxCharLength );
    int headerSize = Integer.BYTES + minCharLength + Integer.BYTES + maxCharLength;

    ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
    byte[] binary = compressor.decompress( columnBinary.binary , columnBinary.binaryStart + headerSize , columnBinary.binaryLength - headerSize );
    ByteBuffer wrapBuffer = ByteBuffer.wrap( binary , 0 , binary.length );
    int minLength = wrapBuffer.getInt();
    int maxLength = wrapBuffer.getInt();
    boolean hasNull = wrapBuffer.getInt() == 1;

    byte[] nullFlagBytes = new byte[columnBinary.rowCount];
    if( hasNull ){
      wrapBuffer.get( nullFlagBytes );
    }

    ILengthMaker lengthMaker = chooseLengthMaker( hasNull , minLength , maxLength );
    int[] lengthArray = lengthMaker.getLengthArray( wrapBuffer , columnBinary.rowCount );

    int currentStart = wrapBuffer.position();
    for( int i = 0 ; i < columnBinary.rowCount ; i++ ){
      if( nullFlagBytes[i] == 0 ){
        allocator.setBytes( i , binary , currentStart , lengthArray[i] );
        currentStart += lengthArray[i];
      } 
      else{
        allocator.setNull( i );
      }
    }

    allocator.setValueCount( columnBinary.rowCount );
  }

  @Override
  public void setBlockIndexNode( final BlockIndexNode parentNode , final ColumnBinary columnBinary , final int spreadIndex ) throws IOException{
    ByteBuffer wrapBuffer = ByteBuffer.wrap( columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    int minLength = wrapBuffer.getInt();
    char[] minCharArray = new char[minLength / Character.BYTES];
    wrapBuffer.asCharBuffer().get( minCharArray );
    wrapBuffer.position( wrapBuffer.position() + minLength );

    int maxLength = wrapBuffer.getInt();
    char[] maxCharArray = new char[maxLength / Character.BYTES];
    wrapBuffer.asCharBuffer().get( maxCharArray );
    wrapBuffer.position( wrapBuffer.position() + maxLength );

    String min = new String( minCharArray );
    String max = new String( maxCharArray );

    BlockIndexNode currentNode = parentNode.getChildNode( columnBinary.columnName );
    currentNode.setBlockIndex( new StringRangeBlockIndex( min , max ) );
  }

  public class RangeStringDicManager implements IDicManager{

    private final PrimitiveObject[] dicArray;

    public RangeStringDicManager( final PrimitiveObject[] dicArray ){
      this.dicArray = dicArray;
    }

    @Override
    public PrimitiveObject get( final int index ) throws IOException{
      return dicArray[index];
    }

    @Override
    public int getDicSize() throws IOException{
      return dicArray.length;
    }

  }

  public class StringColumnManager implements IColumnManager{

    private final ColumnBinary columnBinary;
    private final int binaryStart;
    private final int binaryLength;
    private PrimitiveColumn column;
    private boolean isCreate;

    public StringColumnManager( final ColumnBinary columnBinary , final int binaryStart , final int binaryLength ) throws IOException{
      this.columnBinary = columnBinary;
      this.binaryStart = binaryStart;
      this.binaryLength = binaryLength;
    }

    private void create() throws IOException{
      if( isCreate ){
        return;
      }
      ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
      byte[] binary = compressor.decompress( columnBinary.binary , binaryStart , binaryLength );
      ByteBuffer wrapBuffer = ByteBuffer.wrap( binary , 0 , binary.length );
      int minLength = wrapBuffer.getInt();
      int maxLength = wrapBuffer.getInt();
      boolean hasNull = wrapBuffer.getInt() == 1;

      byte[] nullFlagBytes = new byte[columnBinary.rowCount];
      if( hasNull ){
        wrapBuffer.get( nullFlagBytes );
      }

      ILengthMaker lengthMaker = chooseLengthMaker( hasNull , minLength , maxLength );
      int[] lengthArray = lengthMaker.getLengthArray( wrapBuffer , columnBinary.rowCount );
      PrimitiveObject[] dicArray = new PrimitiveObject[ columnBinary.rowCount ];
      int currentStart = wrapBuffer.position();
      for( int i = 0 ; i < columnBinary.rowCount ; i++ ){
        if( nullFlagBytes[i] == 0 ){
          dicArray[i] = new UTF8BytesLinkObj( binary , currentStart , lengthArray[i] );
          currentStart += lengthArray[i];
        }
      }

      column = new PrimitiveColumn( columnBinary.columnType , columnBinary.columnName );
      IDicManager dicManager = new RangeStringDicManager( dicArray );
      column.setCellManager( new BufferDirectCellManager( columnBinary.columnType , dicManager , columnBinary.rowCount ) );
      column.setIndex( new SequentialStringCellIndex( dicManager ) );

      isCreate = true;
    }

    @Override
    public IColumn get(){
      try{
        create();
      }catch( IOException e ){
        throw new UncheckedIOException( e );
      }
      return column;
    }

    @Override
    public List<String> getColumnKeys(){
      return new ArrayList<String>();
    }

    @Override
    public int getColumnSize(){
      return 0;
    }

  }

}
