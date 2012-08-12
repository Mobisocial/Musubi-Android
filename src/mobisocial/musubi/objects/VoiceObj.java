/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.objects;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.CorralDownloadClient;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tuenti.androidilbc.Codec;

/**
 * A short audio clip. "Version 0" uses a sample rate of 8000, mono channel, and
 * 16bit pcm recording.
 */
public class VoiceObj extends DbEntryHandler implements FeedRenderer, Activator {
	public static final String TAG = "VoiceObj";

    public static final String TYPE = "voice";
    public static final String TYPE_CAF = "audio/x-caf";

    private static final int RECORDER_BPP = 16;
	private static final int RECORDER_SAMPLERATE = 8000;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;


    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(byte[] data) {
        return new MemObj(TYPE, new JSONObject(), data);
    }
    public static JSONObject json(byte[] data){
        String encoded = Base64.encodeToString(data, Base64.DEFAULT);
        JSONObject obj = new JSONObject();
        try{
            obj.put("data", encoded);
        }catch(JSONException e){}
        return obj;
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	ImageView imageView = new ImageView(context);
		imageView.setImageResource(R.drawable.play);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.WRAP_CONTENT,
                                      LinearLayout.LayoutParams.WRAP_CONTENT));
        return imageView;
    }

    @Override
	public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) {
    	// all done!
	}

	@Override
    public void activate(final Context context, final DbObj obj) {
		if (obj.getJson() != null && TYPE_CAF.equalsIgnoreCase(
	    		obj.getJson().optString(CorralDownloadClient.OBJ_MIME_TYPE))) {
	    	new PlayCafFileTask(context, obj).execute();
	    } else {
	    	Runnable r = runnableForWav(context, obj);
	    	if (context instanceof Activity) {
	            ((Activity)context).runOnUiThread(r);
	        } else {
	            r.run();
	        }
	    }
        
    }

	static class CafAudioFormat {
		public static final int FORMAT_ILBC = 0x696C6263;
		public static final int FORMAT_PCM = 0x6C70636D;

		byte[] rawData;
		double sampleRate;
		int formatId;
		int formatFlags;
		int bytesPerPacket;
		int framesPerPacket;
		int channelsPerFrame;
		int bitsPerChannel;

		int dataOffset;
		long dataLength;

		public String toString() {
			return String.format("[caff: %f %d %d %d %d %d %d, head=%d,size=%d]", sampleRate,
			formatId, formatFlags, bytesPerPacket, framesPerPacket,
			channelsPerFrame, bitsPerChannel, dataOffset, dataLength);
		}
	}

	private CafAudioFormat parseAsCaff(byte[] audioData) {
		final int CAFF = ((int)'c' << 24) | ((int)'a' << 16) | ((int)'f' << 8) | (int)'f';
		final int DESC = ((int)'d' << 24) | ((int)'e' << 16) | ((int)'s' << 8) | (int)'c';
		final int DATA = ((int)'d' << 24) | ((int)'a' << 16) | ((int)'t' << 8) | (int)'a';
		final int INFO = ((int)'i' << 24) | ((int)'n' << 16) | ((int)'f' << 8) | (int)'o';
		final int PAKT = ((int)'p' << 24) | ((int)'a' << 16) | ((int)'k' << 8) | (int)'t';
		final int CHAN = ((int)'c' << 24) | ((int)'h' << 16) | ((int)'a' << 8) | (int)'n';
		final int KUKI = ((int)'k' << 24) | ((int)'u' << 16) | ((int)'k' << 8) | (int)'i';
		final int STRG = ((int)'s' << 24) | ((int)'t' << 16) | ((int)'r' << 8) | (int)'g';
		final int MARK = ((int)'m' << 24) | ((int)'a' << 16) | ((int)'r' << 8) | (int)'k';
		final int REGN = ((int)'r' << 24) | ((int)'e' << 16) | ((int)'g' << 8) | (int)'n';
		final int UMID = ((int)'u' << 24) | ((int)'m' << 16) | ((int)'i' << 8) | (int)'d';
		final int OVVW = ((int)'o' << 24) | ((int)'v' << 16) | ((int)'v' << 8) | (int)'w';
		final int PEAK = ((int)'p' << 24) | ((int)'e' << 16) | ((int)'a' << 8) | (int)'k';
		final int INST = ((int)'i' << 24) | ((int)'n' << 16) | ((int)'s' << 8) | (int)'t';
		final int MIDI = ((int)'m' << 24) | ((int)'i' << 16) | ((int)'d' << 8) | (int)'i';
		final int EDCT = ((int)'e' << 24) | ((int)'d' << 16) | ((int)'c' << 8) | (int)'t';
		final int UUID = ((int)'u' << 24) | ((int)'u' << 16) | ((int)'i' << 8) | (int)'d';
		final int FREE = ((int)'f' << 24) | ((int)'r' << 16) | ((int)'e' << 8) | (int)'e';

		ByteBuffer data = ByteBuffer.wrap(audioData);
		data.order(ByteOrder.BIG_ENDIAN);
		final int magicNumber = data.getInt();
		final int fileVersion = data.getShort();
		final int fileFlags = data.getShort();

		if (magicNumber != CAFF) {
			Log.w(TAG, "Not a CAFF file");
			return null;
		}

		CafAudioFormat caff = new CafAudioFormat();
		caff.rawData = audioData;
		while(true) {
			int chunkType = data.getInt();
			long chunkSize = data.getLong();

			switch (chunkType) {
			case DESC:
				data.mark();
				caff.sampleRate = data.getDouble();
				caff.formatId = data.getInt();
				caff.formatFlags = data.getInt();
				caff.bytesPerPacket = data.getInt();
				caff.framesPerPacket = data.getInt();
				caff.channelsPerFrame = data.getInt();
				caff.bitsPerChannel = data.getInt();
				data.reset();
				break;
			case DATA:
				if (caff == null) {
					Log.w(TAG, "no caff description");
					return null;
				}
				caff.dataLength = chunkSize - 4; // 32-bit edit count
				caff.dataOffset = data.position() + 4; // can suck a dick
				Log.d(TAG, "Audio format: " + caff);
				return caff;
			case INFO:
				Log.d(TAG, "INFO Header size " + chunkSize);
				break;
			case PAKT:
				Log.d(TAG, "PAKT Header size " + chunkSize);
				break;
			case CHAN:
				Log.d(TAG, "CHAN Header size " + chunkSize);
				break;
			case KUKI:
				Log.d(TAG, "KUKI Header size " + chunkSize);
				break;
			case STRG:
				Log.d(TAG, "STRG Header size " + chunkSize);
				break;
			case MARK:
				Log.d(TAG, "MARK Header size " + chunkSize);
				break;
			case REGN:
				Log.d(TAG, "REGN Header size " + chunkSize);
				break;
			case UMID:
				Log.d(TAG, "UMID Header size " + chunkSize);
				break;
			case OVVW:
				Log.d(TAG, "OVVW Header size " + chunkSize);
				break;
			case PEAK:
				Log.d(TAG, "PEAK Header size " + chunkSize);
				break;
			case MIDI:
				Log.d(TAG, "MIDI Header size " + chunkSize);
				break;
			case INST:
				Log.d(TAG, "INST Header size " + chunkSize);
				break;
			case EDCT:
				Log.d(TAG, "EDCT Header size " + chunkSize);
				break;
			case UUID:
				Log.d(TAG, "UUID Header size " + chunkSize);
				break;
			case FREE:
				Log.d(TAG, "FREE Header size " + chunkSize);
			default:
				Log.d(TAG, "Unused header " + chunkType);
			}
			data.position(data.position() + (int)chunkSize);
		}
	}

	class PlayCafFileTask extends AsyncTask<Void, Void, Void> {
		AudioTrack mAudioTrack;
		final Context mContext;
		final DbObj mObj;

		public PlayCafFileTask(Context context, DbObj obj) {
			mContext = context;
			mObj = obj;
		}

		@Override
		protected void onPreExecute() {
			int stream = AudioManager.STREAM_MUSIC;
        	AudioManager audioManager = 
        	        (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        	int v = audioManager.getStreamVolume(stream);
        	int m = audioManager.getStreamMaxVolume(stream);
        	if (((float)v / m) < 0.15) {
        	    audioManager.setStreamVolume(stream, v, AudioManager.FLAG_SHOW_UI);
        	}
		}

		@Override
		protected Void doInBackground(Void... params) {
			byte[] caffData = mObj.getRaw();
    		CafAudioFormat caff = parseAsCaff(caffData);
    		if (caff == null) {
    			Log.e(TAG, "not a caff file");
    			return null;
    		}

    		switch (caff.formatId) {
    		case CafAudioFormat.FORMAT_ILBC:
    			playIlbc(caff);
    			break;
    		case CafAudioFormat.FORMAT_PCM:
    			playPcm(caff);
    			break;
			default:
				Log.w(TAG, "Unsupported CAF format " + caff.formatId);
    		}
        	
    		return null;
		}

		void playPcm(CafAudioFormat caff) {
			int bufferSize = 4 * (int)caff.sampleRate;
			mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, (int)caff.sampleRate,
        			AudioFormat.CHANNEL_CONFIGURATION_MONO,
        			AudioFormat.ENCODING_PCM_16BIT,
        			bufferSize, AudioTrack.MODE_STREAM);
        	mAudioTrack.play();
        	mAudioTrack.write(caff.rawData, caff.dataOffset, (int)caff.dataLength);
        	mAudioTrack.stop();
		}

		void playIlbc(CafAudioFormat caff) {
			final int bytesInWavPerSecond = 8000 * 2 * 1; // 8khz 16bit mono pcm
        	final short msPerFrame;
        	if (caff.bytesPerPacket == 38) {
        		msPerFrame = 20;
        	} else if (caff.bytesPerPacket == 50) {
        		msPerFrame = 30;
        	} else {
        		Log.w(TAG, "invalid ilbc");
        		return;
        	}
        	final int maxReadLength = 20 * caff.bytesPerPacket; // (samples/second)/((ms/second)/(ms/frame))
        	int bufferSize = 2*bytesInWavPerSecond;
        	mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, (int)caff.sampleRate,
        			AudioFormat.CHANNEL_CONFIGURATION_MONO,
        			AudioFormat.ENCODING_PCM_16BIT,
        			bufferSize, AudioTrack.MODE_STREAM);
        	mAudioTrack.play();

        	Codec codec = Codec.instance();
    		synchronized (codec) {
    			codec.resetDecoder();
	    		byte[] rawAudio = new byte[bufferSize];
	    		
	    		int offset = caff.dataOffset;
	    		while (offset < caff.dataOffset + caff.dataLength) {
	    			int length = Math.min(maxReadLength, caff.dataOffset + (int)caff.dataLength - offset);
	    			int read = codec.decode(caff.rawData, offset, length, msPerFrame, rawAudio);
	    			if (read <= 0) {
	    				Log.e(TAG, "Error reading data");
	    				break;
	    			}
	    			int frameCount = read / caff.bytesPerPacket;
	    			int msInAudio = msPerFrame * frameCount;
	    			int bytesInWav = bytesInWavPerSecond * msInAudio / 1000;

	    			mAudioTrack.write(rawAudio, 0, bytesInWav);
	    			offset += read;
	    		}
	    		mAudioTrack.stop();

	    		codec.resetDecoder();
    		}
		}
	}

	Runnable runnableForWav(final Context context, final DbObj obj) {
		return new Runnable() {
	        @Override
	        public void run() {
	        	byte[] bytes = obj.getRaw();

	        	int stream = AudioManager.STREAM_MUSIC;
	        	AudioManager audioManager = 
	        	        (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
	        	int v = audioManager.getStreamVolume(stream);
	        	int m = audioManager.getStreamMaxVolume(stream);
	        	if (((float)v / m) < 0.15) {
	        	    audioManager.setStreamVolume(stream, v, AudioManager.FLAG_SHOW_UI);
	        	}

                File file = new File(getTempFilename());
                OutputStream out = null;
                try {
                    out = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(out);
                    bos.write(bytes, 0, bytes.length);
                    bos.flush();
                    bos.close();

                    File tempFile = new File(getTempFilename());
                    copyWaveFile(tempFile,getFilename());
                    tempFile.delete();
                       
                    MediaPlayer mp = new MediaPlayer();
                    mp.setAudioStreamType(stream);
                    mp.setDataSource(getFilename());
                    mp.prepare();
                    mp.start();
                } catch (Exception e) {
                    Log.e(TAG, "error playign audio", e);
                } finally {
                	try {
						if(out != null) out.close();
					} catch (IOException e) {
						Log.e(TAG, "failed to close output stream for voice", e);
					}
                }
	        }
	    };
	}

	private String getTempFilename(){
        return Environment.getExternalStorageDirectory().getAbsolutePath()+"/temp.raw";
    }

    private String getFilename(){
        return Environment.getExternalStorageDirectory().getAbsolutePath()+"/temp.wav";
    }

	private void copyWaveFile(File inFile,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING);
        byte[] data = new byte[bufferSize];
        
        try {
                in = new FileInputStream(inFile);
                out = new FileOutputStream(outFilename);
                totalAudioLen = in.getChannel().size();
                totalDataLen = totalAudioLen + 36;
                
                Log.w("PlayAllAudioAction", "File size: " + totalDataLen);
                
                WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                                longSampleRate, channels, byteRate);
                
                while(in.read(data) != -1){
                        out.write(data);
                }
                
                in.close();
                out.close();
        } catch (FileNotFoundException e) {
                e.printStackTrace();
        } catch (IOException e) {
                e.printStackTrace();
        } finally {
        	try {
				if(in != null) in.close();
				if(out != null) out.close();
			} catch (IOException e) {
				Log.e(TAG, "failed to close output stream for voice", e);
			}
        }
    }

    private void WriteWaveFileHeader(
                    FileOutputStream out, long totalAudioLen,
                    long totalDataLen, long longSampleRate, int channels,
                    long byteRate) throws IOException {
                
        byte[] header = new byte[44];
        
        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + " sent a voice message.");
	}
}
