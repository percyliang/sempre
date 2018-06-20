package edu.stanford.nlp.sempre.roboy.lexicons.word2vec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.roboy.config.ConfigManager;

/**
 * Utility class to load toy data from the internet if necessary.
 * May be refactored into something bigger and more useful later.
 */
public class ToyDataGetter {

    private final boolean verbose;
    private final boolean google;

    public static Gson gson = new Gson();
    private Map<String,String> file_map = new HashMap();

    public ToyDataGetter(boolean verbose) throws Exception{
        this.google = ConfigManager.WORD2VEC_GOOGLE;
        this.file_map = ConfigManager.WORD2VEC_MODELS;
        this.verbose = verbose;
    }


    public String getToyDataFilePath() {
        return file_map.get("dataFilePath");
    }
    public String getToyModelFilePath(){
        if (!this.google)
            return file_map.get("roboyModelFilePath");
        else
            return file_map.get("googleModelFilePath");

    }
    public String getOutputFilePath(){
        return file_map.get("outputFilePath");
    }

    /**
     * Checks if toy data is present on the hard drive. It will be downloaded if necessary.
     */
    public void ensureToyDataIsPresent() {

        // check if already downloaded
        if (fileExists(file_map.get("dataFilePath"))) {
            if (verbose) System.out.println("\nFound data file (" + file_map.get("dataFilePath") + ")");
            return;
        }

        // need to download
        try {
            if (verbose) System.out.println("\nData file is missing and will be downloaded to " + file_map.get("dataFilePath"));

            // make sure directory exists
            File dir = new File(file_map.get("dataDirectory"));
            dir.mkdirs();

            downloadData(file_map.get("dataInetURL"), file_map.get("dataFilePath"));

        } catch (IOException e) {
            System.err.println("\nSorry, couldn't download toy data! Exception: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }

    }

    public void ensureToyModelIsPresent() {

        String modelFilePath;
        // check if already downloaded
        if (!google) {
            modelFilePath = file_map.get("roboyModelFilePath");
        }
        else{
            modelFilePath = file_map.get("googleModelFilePath");
        }

        if (fileExists(modelFilePath)) {
            if (verbose) System.out.println("Found data file (" + modelFilePath + ")");
            return;
        }

        // need to download
        try {
            if (verbose) System.out.println("Model file is missing and will be downloaded to " + file_map.get("modelFilePath"));

            // make sure directory exists
            File dir = new File(modelFilePath);
            dir.mkdirs();

            downloadData(file_map.get("dataInetURL"), modelFilePath);

        } catch (IOException e) {
            System.err.println("Sorry, couldn't download toy data! Exception: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }

    }

    private void downloadData(String fromURL, String toFilePath) throws IOException {
        URL website = new URL(fromURL);
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        FileOutputStream fos = new FileOutputStream(toFilePath);
        long bytesTransferred = fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        if (verbose) {
            System.out.println("Download complete, saved " + bytesTransferred + " bytes to " + toFilePath);
        }

    }

    private boolean fileExists(String filePath) {
        File data = new File(filePath);
        return data.exists();
    }

}
