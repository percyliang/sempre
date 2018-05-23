package edu.stanford.nlp.sempre.roboy.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import fig.basic.LogInfo;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private static String yamlConfigFile = "parser.properties";

    // Endpoints
    public static String DB_SEARCH = "http://lookup.dbpedia.org/api/search.asmx/KeywordSearch?QueryString=";
    public static String DB_SPARQL = "http://dbpedia.org/sparql/";
    public static String MCG_SEARCH = "https://concept.research.microsoft.com/api/Concept/ScoreByProb?instance=";

    // JSON files
    public static String SCHEMA_FILE = "lib/fb_data/93.exec/schema2.ttl";
    public static String GLOSSARY_FILE = "src/main/java/edu/stanford/nlp/sempre/roboy/data/database_glossary.json";
    public static String DATABASE_FILE = "src/main/java/edu/stanford/nlp/sempre/roboy/data/database_indexing.json";
    public static String WORD2VEC_FILE = "src/main/java/edu/stanford/nlp/sempre/roboy/data/word2vec.json";
    public static String SCORE_WEIGHTS_FILE = "src/main/java/edu/stanford/nlp/sempre/roboy/data/score_weights.json";
    public static String DB_TYPES_FILE = "src/main/java/edu/stanford/nlp/sempre/roboy/data/database_names.json";
    public static String FOLLOW_FILE = "src/main/java/edu/stanford/nlp/sempre/roboy/data/follow_up_patterns.json";

    // Flags
    public static int DEBUG = 1;

    // Other parameters
    public static double W2V_THRES = 0.3;
    public static int CONTEXT_DEPTH = 3;
    public static double FOLLOW_THRES = 0.3;
    public static double LEXICON_THRES = 0.3;
    public static boolean WORD2VEC_GOOGLE = false;
    public static String[] DB_KEYWORDS = {"Result","Label","URI","Refcount"};
    public static String[] MCG_KEYWORDS = {"KeyValueOfstringdouble","Key","Value"};
    public static String[] KEYWORDS_TAGS = {"NNP","NN","NP"};

    // JSON objects to be read
    public static Map<String, List<String>> FOLLOW_UPS = new HashMap<>();
    public static Map<String, Double> SCORING_WEIGHTS = new HashMap<>();
    public static Map<String, String> WORD2VEC_MODELS = new HashMap<>();
    public static Map<String, String> DB_GLOSSARY = new HashMap<>();
    public static Map<String, Map<String,String> > DB_TYPES = new HashMap<>();

    static {
        // this block is called once at and will initialize config
        // alternative: create a singleton for this class
        initializeConfig();
    }

    /**
     * This function reads the YAML config file and initializes all fields.
     * It is called only once at the beginning
     */
    private static void initializeConfig() {
        LogInfo.begin_track("Initializing Config");

        // Init all local variables
        YAMLConfiguration yamlConfig = new YAMLConfiguration();
        Gson gson = new Gson();

        try
        {
            File propertiesFile = new File(yamlConfigFile);
            if (!propertiesFile.exists()) { // propertiesFile == null doesn't work!
                LogInfo.error("Could not find "+yamlConfigFile+" file in project path! YAML configurations will be unavailable.");
                return;
            }
            FileReader propertiesReader = new FileReader(propertiesFile);
            yamlConfig.read(propertiesReader);

            // Endpoints
            DB_SEARCH   = yamlConfig.getString("DB_SEARCH");
            DB_SPARQL   = yamlConfig.getString("DB_SPARQL");
            MCG_SEARCH  = yamlConfig.getString("MCG_SEARCH");

            // JSON files
            GLOSSARY_FILE        = yamlConfig.getString("GLOSSARY_FILE");
            DATABASE_FILE        = yamlConfig.getString("DATABASE_FILE");
            WORD2VEC_FILE        = yamlConfig.getString("WORD2VEC_FILE");
            SCORE_WEIGHTS_FILE   = yamlConfig.getString("SCORE_WEIGHTS_FILE");
            DB_TYPES_FILE        = yamlConfig.getString("TYPES_FILE");
            SCHEMA_FILE          = yamlConfig.getString("SCHEMA_FILE");
            FOLLOW_FILE          = yamlConfig.getString("FOLLOW_FILE");

            DEBUG           = yamlConfig.getInt("DEBUG");

            WORD2VEC_GOOGLE       = yamlConfig.getBoolean("WORD2VEC_GOOGLE");
            W2V_THRES       = yamlConfig.getDouble("W2V_THRES");
            CONTEXT_DEPTH     = yamlConfig.getInt("CONTEXT_DEPTH");

            DB_KEYWORDS     = yamlConfig.getStringArray("DB_KEYWORDS");
            MCG_KEYWORDS    = yamlConfig.getStringArray("MCG_KEYWORDS");
            KEYWORDS_TAGS    = yamlConfig.getStringArray("KEYWORDS_TAGS");
            FOLLOW_THRES       = yamlConfig.getDouble("FOLLOW_THRES");
            LEXICON_THRES       = yamlConfig.getDouble("LEXICON_THRES");

            // Read all parameters from JSON files
            JsonReader reader = new JsonReader(new FileReader(SCORE_WEIGHTS_FILE));
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            SCORING_WEIGHTS = gson.fromJson(reader, type);

            reader = new JsonReader(new FileReader(WORD2VEC_FILE));
            type = new TypeToken<Map<String, String>>(){}.getType();
            WORD2VEC_MODELS = gson.fromJson(reader, type);

            reader = new JsonReader(new FileReader(GLOSSARY_FILE));
            type = new TypeToken<Map<String, String>>(){}.getType();
            DB_GLOSSARY = gson.fromJson(reader, type);

            reader = new JsonReader(new FileReader(DB_TYPES_FILE));
            type = new TypeToken<Map<String, Map<String,String> >>(){}.getType();
            DB_TYPES = gson.fromJson(reader, type);

            reader = new JsonReader(new FileReader(FOLLOW_FILE));
            type = new TypeToken<Map<String, List<String> >>(){}.getType();
            FOLLOW_UPS = gson.fromJson(reader, type);

        } catch(ConfigurationException | IOException e) {
            LogInfo.errors("Exception while reading YAML configurations from %s", yamlConfigFile);
            LogInfo.errors(e.getMessage());
        }
    }


}
