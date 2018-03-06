package edu.stanford.nlp.sempre.freebase;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.freebase.FbFormulasInfo.BinaryFormulaInfo;
import edu.stanford.nlp.sempre.MergeFormula.Mode;
import edu.stanford.nlp.sempre.freebase.utils.FileUtils;
import edu.stanford.nlp.sempre.freebase.utils.FormatConverter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Option;
import fig.exec.Execution;
import fig.prob.SampleUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * One-time hack that converts the Cai & Yates dataset to our format.
 * @author Jonathan Berant
 */
public class Free917Converter implements Runnable {

  private Counter<Integer> argnumCounter = new ClassicCounter<Integer>();
  private FbFormulasInfo formulaInfo = FbFormulasInfo.getSingleton();
  private Set<String> cvts;
  @Option(gloss = "Input path to examples to canonicalize")
  public String inDir;
  @Option(gloss = "Input path to examples to canonicalize")
  public String outDir;
  @Option(gloss = "Input path to examples to canonicalize")
  public String entityInfoFile;
  @Option(gloss = "Input path to examples to canonicalize")
  public String cvtFile;
  @Option(gloss = "Input path to examples to canonicalize")
  public String midToIdFile;

  @Override
  public void run() {
    try {
      String inQuestionsFile = inDir + "question-and-logical-form-917/dataset-all-917.txt";
      String inNpFile = inDir + "fixed-np-manually.txt";
      String outQuestionFile = outDir + "dataset-all-917_corrected.txt";
      String outNpFile = outDir + "fixed-np-manually_corrected.txt";
      String outputPrefix = outDir + "free917";
      String free917EntityInfoFile = outDir + "entityInfo.txt";
      String free917MissingEntitiesFile = outDir + "missingEntities.txt";
      cvts = FileUtils.loadSet(cvtFile);

      correctErrors(inQuestionsFile, inNpFile, outQuestionFile, outNpFile);
      convertExampleFile(outQuestionFile, outputPrefix);
      genreateEntityInfoFile(outNpFile, entityInfoFile, free917EntityInfoFile, free917MissingEntitiesFile);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws IOException {
    Execution.run(args, new Free917Converter());
  }

  private void correctErrors(String inQuestionsFile, String inNpFile,
                             String outQuestionFile, String outNpFile) throws IOException {

    // manual corrections of question file
    int i = 0;
    PrintWriter questionWriter = IOUtils.getPrintWriter(outQuestionFile);
    for (String line : IOUtils.readLines(inQuestionsFile)) {
      if (line.equals("(lambda $0 /type/int (exists $1 (/award/ranking@rank@year@note@list@item:t $0 /type/datetime/2000:/type/datetime $1 /en/fortune_500:/award/ranked_list /en/monsanto:/award/ranked_item)))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (/award/ranking@rank@year@note@list@item:t $0 /type/datetime/2000:/type/datetime $1 $2 /en/fortune_500:/award/ranked_list /en/monsanto:/award/ranked_item)))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (exists $4 (/medicine/disease/survival_rates&/medicine/survival_rate@gender@race@years@rate@disease_stage:t /en/prostate_cancer:/medicine/disease $1 $2 $0 $3 $4))))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (exists $4 (/medicine/disease/survival_rates&/medicine/survival_rate@gender@race@years@rate@disease_stage:t /en/prostate_cancer:/medicine/disease $-1 $1 $2 $3 $0 $4))))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/soccer/football_league_participation@team@league@from@to:t $1 $0 $2 $3)))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/soccer/football_league_participation@team@league@from@to:t /en/real_madrid:/soccer/football_team $1 $0 $2 $3)))))");
      else if (line.equals("(lambda $0 /common/topic (/tv/tv_program@languages:t /base/ranker/rankerurlname/firefly$002f143400:/tv/tv_program $0))"))
        questionWriter.println("(lambda $0 /common/topic (/tv/tv_program@languages:t /m/014v3t:/tv/tv_program $0))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/film/film/estimated_budget&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/edward_scissorhands:/film/film $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/film/film/estimated_budget&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/edward_scissorhands:/film/film $-1 $1 $0 $2))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/film/film/estimated_budget&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/transformers:/film/film $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/film/film/estimated_budget&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/transformers:/film/film $-1 $1 $0 $2))))");
      else if (line.equals("(lambda $0 /location/location (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (/library/public_library/address&/location/mailing_address@street_address@street_address_2@citytown@postal_code@state_province_region@country:t /m/02ncllz:/library/public_library $1 $2 $0 $3 $4 $5)))))))"))
        questionWriter.println("(lambda $0 /location/location (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (/library/public_library/address&/location/mailing_address@street_address@street_address_2@citytown@postal_code@state_province_region@country:t /m/02ncllz:/library/public_library $-1 $1 $2 $0 $3 $4 $5)))))))");
      else if (line.matches("who won ali.*frazier ii"))
        questionWriter.println("who won muhammad ali vs. joe frazier ii");
      else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (/base/boxing/match_boxer_relationship@match@boxer@winner_won@points:t $1 $0 $2 $3)))))")) {
        if (i++ == 0)
          questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (/boxing/match_boxer_relationship@match@boxer@winner_won@points:t /en/ali-frazier_ii:/boxing/boxing_match $1 $0 /type/boolean/true $2 $3)))))");
        else
          questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (/boxing/match_boxer_relationship@match@boxer@winner_won@points:t /m/0kvlz:/boxing/boxing_match $1 $0 $2 $3)))))");
      } else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (exists $6 (/base/boxing/boxing_title_tenure@champion@weight@from@to@defenses@title@notes:t $0 $1 $2 $3 $4 $5 $6))))))))"))
        questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (exists $6 (/boxing/boxing_title_tenure@champion@weight@from@to@defenses@title@notes:t $0 $1 $2 $3 $4 /m/0chgh2j:/boxing/boxing_title $5 $6))))))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/business_operation/revenue&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/motorola:/business/business_operation $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/business_operation/revenue&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/motorola:/business/business_operation $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $1 /common/topic (exists $2 (exists $3 (/business/business_operation/net_profit&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/procter_gamble:/business/business_operation $1 $2 $3))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/business/business_operation/net_profit&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/procter_gamble:/business/business_operation $-1 $1 $0 $2))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (/business/business_operation/revenue&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/viacom:/business/business_operation /un/2009:/type/datetime $0 $1)))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (/business/business_operation/revenue&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/viacom:/business/business_operation /type/datetime/2009:/type/datetime $1 $0)))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/business/business_operation/operating_income&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/j_c_penney:/business/business_operation $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/business/business_operation/operating_income&/measurement_unit/dated_money_value@valid_date@amount@currency:t /en/j_c_penney:/business/business_operation $-1 $1 $0 $2))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/belgium:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/belgium:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/iowa:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/iowa:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/location/statistical_region/major_exports&/location/imports_exports_by_industry@amount@currency@date@industry:t /en/madagascar:/location/statistical_region $1 $2 $3 $0)))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/location/statistical_region/major_exports&/location/imports_exports_by_industry@amount@currency@date@industry:t /en/madagascar:/location/statistical_region $-1 $1 $2 $3 $0)))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/africa:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/africa:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/asia:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/asia:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/earth:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/earth:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/europe:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/europe:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/earth:/location/statistical_region $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (/location/statistical_region/population&/measurement_unit/dated_integer@number@year@source:t /en/earth:/location/statistical_region $-1 $0 $1 $2))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (/tv/regular_tv_appearance@actor@character@series@from@to@special_performance_type@seasons:t /en/ron_glass:/tv/tv_actor $1 $0 $2 $3 $4 $5)))))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (/tv/regular_tv_appearance@actor@character@series@from@to@special_performance_type@seasons:t /en/ron_glass:/tv/tv_actor $1 $2 $0 $3 $4 $5)))))))");
      else if (line.equals("(lambda $0 /common/topic (/fashion/garment@specialization_of:t /fashion/garment:/fashion/garment $0))"))
        questionWriter.println("(lambda $0 /common/topic (/fashion/garment@specialization_of:t /en/knickerbockers:/fashion/garment $0))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (exists $4 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $2 /en/golden_globe_award_for_best_motion_picture_-_drama:/award/award_category $3 $0 $4))))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (exists $4 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $2 /en/golden_globe_award_for_best_motion_picture_-_drama:/award/award_category $3 $4 $0))))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/celebrities/sexual_orientation_phase@celebrity@start@end@sexual_orientation:t /en/britney_spears:/celebrities/celebrity $1 $2 $0))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/celebrities/sexual_orientation_phase@celebrity@start@end@sexual_orientation:t /en/britney_spears:/celebrities/celebrity $1 $2 $3 $0))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/projects/project_participation@project@participant@role@from_date@to_date:t /m/0gk9x46:/projects/project /en/francesco_sabatini:/projects/project_participant $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/projects/project_participation@project@participant@role@from_date@to_date:t /m/0gk9x46:/projects/project $1 /en/francesco_sabatini:/projects/project_participant $2 $3 $0))))");
      else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (exists $6 (/government/government_position_held@office_holder@office_position_or_title@governmental_body@district_represented@appointed_by@from@to@jurisdiction_of_office@legislative_sessions:t $0 /en/united_states_senator:/government/government_office_or_title $1 $2 $3 $4 $5 /en/colorado:/government/governmental_jurisdiction $6))))))))"))
        questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (exists $6 (/government/government_position_held@office_holder@office_position_or_title@governmental_body@district_represented@appointed_by@from@to@jurisdiction_of_office@legislative_sessions:t $0 /en/united_states_senator:/government/government_office_or_title $1 $2 /en/colorado:/government/governmental_jurisdiction $3 $4 $5 $6 $7 $8))))))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (/ice_hockey/hockey_previous_roster_position@team@player@from@to:t /en/montreal_canadiens:/ice_hockey/hockey_team /en/christopher_higgins:/ice_hockey/hockey_player $0 $1)))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (/ice_hockey/hockey_previous_roster_position@team@player@from@to:t /en/montreal_canadiens:/ice_hockey/hockey_team /en/christopher_higgins:/ice_hockey/hockey_player $1 $2 $0)))");
      else if (line.equals("(lambda $0 /location/location (/organization/organization@headquarters:t /en/apple_inc:/organization/organization $0))"))
        questionWriter.println("(lambda $0 /location/location (exists $1 (exists $2 (exists $3 (exists $4 (/organization/organization/headquarters&/location/mailing_address@citytown@race@years@rate@disease_stage:t /en/apple_inc:/organization/organization $-1 $0))))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (/celebrities/substance_abuse_problem@substance@start@end@celebrity:t /en/cocaine:/celebrities/abused_substance $0 $1 /en/robin_williams:/celebrities/celebrity)))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (/celebrities/substance_abuse_problem@substance@start@end@celebrity:t /en/cocaine:/celebrities/abused_substance $1 $2 $0 /en/robin_williams:/celebrities/celebrity)))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/travel/transportation@travel_destination@mode_of_transportation@transport_operator@transport_terminus:t /en/paris:/travel/travel_destination $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/travel/transportation@travel_destination@mode_of_transportation@transport_operator@transport_terminus:t /en/paris:/travel/travel_destination $1 $2 $0))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (/martial_arts/martial_arts_certification@person@qualification@certifying_body@date@art:t /en/cathy_landers:/martial_arts/martial_artist /en/fifth_degree:/martial_arts/martial_arts_qualification $1 $0 /en/seishindo_kenpo:/martial_arts/martial_art)))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (/martial_arts/martial_arts_certification@person@qualification@certifying_body@date@art:t /en/cathy_landers:/martial_arts/martial_artist /en/fifth_degree:/martial_arts/martial_arts_qualification $1 $2 $0 /en/seishindo_kenpo:/martial_arts/martial_art)))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (exists $3 (/american_football/football_historical_roster_position@player@team@from@to@number@position_s:t /en/david_akers:/american_football/football_player /en/philadelphia_eagles:/american_football/football_team $1 $2 $0 $3)))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (exists $3 (/american_football/football_historical_roster_position@player@team@from@to@number@position_s:t /en/david_akers:/american_football/football_player /en/philadelphia_eagles:/american_football/football_team $1 $2 $3 $0 $4)))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (exists $4 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $2 /m/04d215m:/award/award_category $3 $0 $4))))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (exists $4 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $2 /m/04d215m:/award/award_category $3 $4 $0))))))");
      else if (line.equals("(count $0 (/architecture/architect@structures_designed:t /en/frank_lloyd_wright:/architecture/architect $0))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (/architecture/architect@structures_designed:t /en/frank_lloyd_wright:/architecture/architect $0)))");
      else if (line.equals("(lambda $0 /common/topic (/conferences/conference_subject@series_of_conferences_about_this:t /en/mathematics:/conferences/conference_subject $0))"))
        questionWriter.println("(count $0 (/conferences/conference_subject@series_of_conferences_about_this:t /en/mathematics:/conferences/conference_subject $0))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/ritz_cracker:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/ritz_cracker:/business/brand $1 $2 $0))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (exists $2 (exists $3 (/american_football/player_game_statistics@player@season@team@games@starts@as_of_week:t /en/donovan_mcnabb:/american_football/football_player /en/2008_nfl_season:/sports/sports_league_season $1 $0 $2 $3)))))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (exists $2 (exists $3 (/american_football/player_game_statistics@player@season@team@games@starts@as_of_week:t /en/donovan_mcnabb:/american_football/football_player /en/2008_nfl_season:/sports/sports_league_season $1 $2 $0 $3)))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (/royalty/chivalric_order_position_tenure@order@chivalric_office@from@until@officer:t /en/order_of_the_most_holy_annunciation:/royalty/order_of_chivalry /en/grand_master:/royalty/chivalric_office $1 $2 $0))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (/royalty/chivalric_order_position_tenure@order@chivalric_office@from@until@officer:t /en/order_of_the_most_holy_annunciation:/royalty/order_of_chivalry /en/grand_master:/royalty/chivalric_office $1 $2 $3 $0))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (exists $3 (/film/performance@actor@film@special_performance_type@character@character_note:t $1 /en/charlies_angels:/film/film $2 $0 $3)))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (exists $3 (/film/performance@actor@film@special_performance_type@character@character_note:t $1 /en/charlies_angels:/film/film $2 $3 $0 $4)))))");
      else if (line.equals("(lambda $0 /location/location (/library/public_library@address:t /en/mitchell_public_library:/library/public_library $0))"))
        questionWriter.println("(lambda $0 /location/location (/library/public_library/address&/location/mailing_address@citytown@street_address:t /m/0j9by57:/library/public_library $-1 $1 $0))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/chips_ahoy:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/chips_ahoy:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (/media_common/dedication@dedicated_by@dedicated_to@work_dedicated@notes:t /en/joseph_haydn:/media_common/dedicator $1 $0 $2))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (/media_common/dedication@dedicated_by@dedicated_to@work_dedicated@notes:t /en/wolfgang_amadeus_mozart:/media_common/dedicator $1 /en/joseph_haydn:/media_common/dedicator $2 $0 $3))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/government/political_party_tenure@politician@from@to@party:t /en/grover_cleveland:/government/politician $1 $2 $0))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/government/political_party_tenure@politician@from@to@party:t /en/grover_cleveland:/government/politician $1 $2 $3 $0))))");
      else if (line.equals("(lambda $0 /location/location (/organization/organization@headquarters:t /en/h_r_block:/organization/organization $0))"))
        questionWriter.println("(lambda $0 /location/location (/organization/organization/headquarters&/location/mailing_address@citytown@street_address:t /en/h_r_block:/organization/organization $-1 $0))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/country_time:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/country_time:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/girl_scouts_of_the_usa:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (/organization/organization@date_founded:t /en/girl_scouts_of_the_usa:/organization/organization $0))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/tostitos:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/tostitos:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/girl_scouts_of_the_usa:/business/brand $0 $1 $2))))"))
        questionWriter.println("(lambda $0 /common/topic (/organization/organization@founders:t /en/girl_scouts_of_the_usa:/business/brand $0))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /m/02r3cjp:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /m/02r3cjp:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/stove_top_stuffing:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/stove_top_stuffing:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/oreo:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/oreo:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (exists $3 (/cvg/musical_game_song_relationship@download@game@platforms@song@release_date:t $1 /en/guitar_hero_aerosmith:/cvg/musical_game $2 $0 $3)))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (exists $3 (/cvg/musical_game_song_relationship@download@game@platforms@song@release_date:t $1 /en/guitar_hero_aerosmith:/cvg/musical_game $2 $3 $0 $4)))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (/olympics/olympic_athlete_affiliation@athlete@country@olympics@sport:t /m/04dnjr9:/olympics/olympic_athlete $1 /en/1992_summer_olympics:/olympics/olympic_games $0)))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (/olympics/olympic_athlete_affiliation@athlete@country@olympics@sport:t /m/04dnjr9:/olympics/olympic_athlete $1 $2 /en/1992_summer_olympics:/olympics/olympic_games $3 $0)))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (/tv/regular_tv_appearance@actor@character@series@from@to@special_performance_type@seasons:t /en/jerry_seinfeld:/tv/tv_actor $1 $0 $2 $3 $4 $5)))))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (exists $5 (/tv/regular_tv_appearance@actor@character@series@from@to@special_performance_type@seasons:t /en/jerry_seinfeld:/tv/tv_actor $1 $2 $0 $3 $4 $5 $6)))))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/basketball/basketball_roster_position@number@player@position@team:t $1 /en/richard_hamilton:/basketball/basketball_player $2 $0))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/basketball/basketball_roster_position@number@player@position@team:t $1 /en/richard_hamilton:/basketball/basketball_player $2 $3 $0))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/crystal_light:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/crystal_light:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/kool-aid:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/kool-aid:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 /type/datetime/1981:/type/datetime $2 /en/danny_devito:/award/award_winner $0 $3)))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 /type/datetime/1981:/type/datetime $2 $3 /en/danny_devito:/award/award_winner $4 $0 $5)))))");
      else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (/american_football/football_historical_roster_position@player@team@from@to@number@position_s:t $0 /en/green_bay_packers:/american_football/football_team $1 $2 $3 /en/quarterback:/american_football/football_position)))))"))
        questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (/american_football/football_historical_roster_position@player@team@from@to@number@position_s:t $0 /en/green_bay_packers:/american_football/football_team $1 $2 $3 $4 /en/quarterback:/american_football/football_position)))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (/event/speech_or_presentation@event@speech_topic@speaker_s@type_or_format_of_presentation@presented_work@date:t $1 /en/world_war_ii:/event/speech_topic $2 $3 $0 $4))))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (/event/speech_or_presentation@event@speech_topic@speaker_s@type_or_format_of_presentation@presented_work@date:t $1 /en/world_war_ii:/event/speech_topic $2 $3 $4 $0 $5))))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (/celebrities/substance_abuse_problem@substance@start@end@celebrity:t /en/cocaine:/celebrities/abused_substance $1 $2 $0))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (/celebrities/substance_abuse_problem@substance@start@end@celebrity:t /en/cocaine:/celebrities/abused_substance $1 $2 $3 $0))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/doritos:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/doritos:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (/event/speech_or_presentation@event@speech_topic@speaker_s@type_or_format_of_presentation@presented_work@date:t $1 $2 /en/winston_churchill:/event/public_speaker $3 $0 $4))))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (exists $3 (exists $4 (/event/speech_or_presentation@event@speech_topic@speaker_s@type_or_format_of_presentation@presented_work@date:t $1 $2 /en/winston_churchill:/event/public_speaker $3 $4 $0 $5))))))");
      else if (line.equals("(lambda $0 /common/topic (/transportation/bridge@bridge_type:t /en/suspension_bridge:/transportation/bridge $0))"))
        questionWriter.println("(lambda $0 /common/topic (/transportation/bridge@bridge_type:t /en/manhattan_bridge:/transportation/bridge $0))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/martial_arts/martial_arts_certification@person@qualification@certifying_body@date@art:t /en/christopher_adams:/martial_arts/martial_artist /en/black_belt:/martial_arts/martial_arts_qualification $1 $2 $0))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/martial_arts/martial_arts_certification@person@qualification@certifying_body@date@art:t /en/christopher_adams:/martial_arts/martial_artist $1 /en/black_belt:/martial_arts/martial_arts_qualification $2 $3 $4 $0))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/capri_sun:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/capri_sun:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (/organization/leadership@person@title@as_of_date@organization@role@from@to:t $0 /en/chief_executive_officer:/type/text $1 /en/save-a-lot:/organization/organization $2 $3 $4))))))"))
        questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (/organization/leadership@person@title@as_of_date@organization@role@from@to:t $0 $1 $2  /en/save-a-lot:/organization/organization $3 /en/chief_executive_officer:/type/text $4 $5))))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/barbie:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/barbie:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/capn_crunch:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/capn_crunch:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/gatorade:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/gatorade:/business/brand $1 $2 $0 $3))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/mountain_dew:/business/brand $1 $0 $2))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (/business/company_brand_relationship@brand@company@from_date@to_date:t /en/mountain_dew:/business/brand $1 $2 $0 $3))))");
        // entity issues
      else if (line.equals("(lambda $0 /common/topic (/music/track@producer:t /m/0l16j8:/music/track $0))"))
        questionWriter.println("(lambda $0 /common/topic (/music/recording@producer:t /m/0l16j8:/music/recording $0))");
      else if (line.equals("(lambda $0 /people/person (/book/written_work@author:t /m/03crp32:/book/written_work $0))"))
        questionWriter.println("(lambda $0 /people/person (/book/written_work@author:t /m/067y_k7:/book/written_work $0))");
      else if (line.equals("(lambda $0 /common/topic (/book/literary_genre@books_in_this_genre:t $0 /en/the_hound_of_the_baskervilles:/book/book))"))
        questionWriter.println("(lambda $0 /common/topic (/media_common/literary_genre@books_in_this_genre:t $0 /en/the_hound_of_the_baskervilles:/book/book))");
      else if (line.equals("(count $0 (/book/literary_genre@books_in_this_genre:t /en/computer_programming:/book/literary_genre $0))"))
        questionWriter.println("(count $0 (/media_common/literary_genre@books_in_this_genre:t /en/computer_programming:/book/literary_genre $0))");
      else if (line.equals("(lambda $0 /type/int (/freebase/type_profile@instance_count:t /film/film_actor:/freebase/type_profile $0))"))
        questionWriter.println("(lambda $0 /type/int (/freebase/type_profile@instance_count:t /film/actor:/freebase/type_profile $0))");
      else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (/olympics/olympic_medal_honor@country@event@medal@medalist@olympics:t $1 /view/en/tennis_at_the_1896_summer_olympics_mens_singles:/olympics/olympic_event_competition /en/gold_medal:/olympics/olympic_medal $0 $2))))"))
        questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (/olympics/olympic_medal_honor@country@event@medal@medalist@olympics:t $1 /en/tennis_at_the_1896_summer_olympics_mens_singles:/olympics/olympic_event_competition /en/gold_medal:/olympics/olympic_medal $0 $2))))");
      else if (line.equals("(lambda $0 /type/datetime (/amusement_parks/ride@opened:t /m/03mfjrv:/amusement_parks/ride $0))"))
        questionWriter.println("(lambda $0 /type/datetime (/amusement_parks/ride@opened:t /m/0flmt0:/amusement_parks/ride $0))");
      else if (line.equals("(lambda $0 /common/topic (/base/dinosaur/dinosaur@diet:t /en/ceratopsia/-/base/dinosaur:/base/dinosaur/dinosaur $0))"))
        questionWriter.println("(lambda $0 /common/topic (/base/dinosaur/dinosaur@diet:t /en/ceratopsia:/base/dinosaur/dinosaur $0))");
      else if (line.equals("(lambda $0 /location/location (/base/dinosaur/dinosaur_location@dinosaur_s:t $0 /en/barosaurus/-/base/dinosaur:/base/dinosaur/dinosaur))"))
        questionWriter.println("(lambda $0 /location/location (/base/dinosaur/dinosaur_location@dinosaur_s:t $0 /en/barosaurus:/base/dinosaur/dinosaur))");
      else if (line.equals("(lambda $0 /common/topic (/chemistry/chemical_element@symbol:t /authority/us/gov/hhs/fda/srs-unii/fxs1by2pgl:/chemistry/chemical_element $0))"))
        questionWriter.println("(lambda $0 /common/topic (/chemistry/chemical_element@symbol:t /m/025sw5g:/chemistry/chemical_element $0))");
      else if (line.equals("(count $0 (/amusement_parks/park@annual_visits:t /en/magic_kingdom:/amusement_parks/park $0))"))
        questionWriter.println("(lambda $0 /type/int (/amusement_parks/park@annual_visits:t /en/magic_kingdom:/amusement_parks/park $0))");
      else if (line.equals("(lambda $0 /common/topic (/organization/organization@slogan:t /business/cik/0001011006:/organization/organization $0))"))
        questionWriter.println("(lambda $0 /common/topic (/organization/organization@slogan:t /m/019rl6:/organization/organization $0))");
      else if (line.equals("(lambda $0 /location/location (/location/location@containedby:t /base/usnris/item/86000083:/location/location $0))"))
        questionWriter.println("(lambda $0 /location/location (/location/location@containedby:t /m/019zhn:/location/location $0))");
      else if (line.equals("(lambda $0 /common/topic (/opera/opera@language:t /base/imslp/65847:/opera/opera $0))"))
        questionWriter.println("(lambda $0 /common/topic (/opera/opera@language:t /m/09hvx:/opera/opera $0))");
      else if (line.equals("(lambda $0 /people/person (/film/film@costume_design_by:t /source/allocine/fr/film/132663:/film/film $0))"))
        questionWriter.println("(lambda $0 /people/person (/film/film@costume_design_by:t /m/04jpg2p:/film/film $0))");
      else if (line.equals("(lambda $0 /people/person (/architecture/architectural_style@architects:t /en/bauhaus:/architecture/architectural_style $0))"))
        questionWriter.println("(lambda $0 /people/person (/architecture/architectural_style@architects:t /en/international_style:/architecture/architectural_style $0))");
      else if (line.equals("(lambda $0 /common/topic (/astronomy/star@temperature_k:t /en/polaris:/astronomy/star $0))"))
        questionWriter.println("(lambda $0 /common/topic (/astronomy/star@temperature_k:t /m/0kjyrc7:/astronomy/star $0))");
      else if (line.equals("(lambda $0 /common/topic (/book/literary_series@fictional_universe:t /en/the_lord_of_the_rings:/book/literary_series $0))"))
        questionWriter.println("(lambda $0 /common/topic (/fictional_universe/work_of_fiction@setting:t /en/the_lord_of_the_rings:/book/literary_series $0))");
      else if (line.equals("(lambda $0 /common/topic (/chemistry/chemical_element@melting_point:t /quotationsbook/subject/gold:/chemistry/chemical_element $0))"))
        questionWriter.println("(lambda $0 /common/topic (/chemistry/chemical_element@melting_point:t /m/025rs2z:/chemistry/chemical_element $0))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (exists $3 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $0 /en/guardian_first_book_award:/award/award_category $2 /en/everything_is_illuminated:/award/award_winning_work $3)))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (exists $3 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $0 /en/guardian_first_book_award:/award/award_category $2 $3 /en/everything_is_illuminated:/award/award_winning_work $4)))))");
      else if (line.equals("(lambda $0 /type/datetime (exists $1 (exists $2 (exists $3 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $0 /en/hugo_award_for_best_novel:/award/award_category $2 /en/harry_potter_and_the_goblet_of_fire:/award/award_winning_work $3)))))"))
        questionWriter.println("(lambda $0 /type/datetime (exists $1 (exists $2 (exists $3 (/award/award_honor@notes_description@year@award@award_winner@honored_for@ceremony:t $1 $0 /en/hugo_award_for_best_novel:/award/award_category $2 $3 /en/harry_potter_and_the_goblet_of_fire:/award/award_winning_work $3)))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (/american_football/football_roster_position@team@player@position@number:t /en/baltimore_ravens:/american_football/football_team /en/ray_lewis:/american_football/football_player $0 $1)))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (/american_football/football_historical_roster_position@team@player@position_s@number:t /en/baltimore_ravens:/american_football/football_team /en/ray_lewis:/american_football/football_player $0 $1)))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/award/award_nomination@award@year@award_nominee@nominated_for@notes_description:t /en/peoples_choice_award_for_favorite_comedy_movie:/award/award_category $1 $2 $0 $3)))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/award/award_nomination@award@year@award_nominee@nominated_for@notes_description:t /en/peoples_choice_award_for_favorite_comedy_movie:/award/award_category $1 $2 $3 $0 $4)))))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (/award/ranking@rank@year@note@list@item:t $0 /type/datetime/2010:/type/datetime $1 /en/fortune_500:/award/ranked_list /en/target_corporation:/award/ranked_item)))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (/award/ranking@rank@year@note@list@item:t $0 /type/datetime/2010:/type/datetime $1 $2 /en/fortune_500:/award/ranked_list /en/target_corporation:/award/ranked_item)))");
      else if (line.equals("(lambda $0 /type/int (exists $1 (/baseball/baseball_roster_position@position@team@player@number:t $1 /en/boston_red_sox:/baseball/baseball_team /en/kevin_youkilis:/baseball/baseball_player $0)))"))
        questionWriter.println("(lambda $0 /type/int (exists $1 (/sports/sports_team_roster@position@team@player@number:t $1 /en/boston_red_sox:/baseball/baseball_team /en/kevin_youkilis:/baseball/baseball_player $0)))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/basketball/basketball_roster_position@number@player@position@team:t $1 /en/keyon_dooling:/basketball/basketball_player $0 $2))))"))
        questionWriter.println("(lambda $0 /common/topic (/basketball/basketball_player@position_s:t /en/keyon_dooling:/basketball/basketball_player $0))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (/business/sponsorship@sponsored_by@from@to@sponsored_recipient:t $0 $1 $2 /en/gatorade:/business/sponsored_recipient))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (/business/sponsorship@sponsored_by@from@to@sponsored_recipient:t /en/gatorade:/business/sponsored_recipient $1 $2 $3 $0))))");
      else if (line.equals("(lambda $0 /common/topic (/freebase/domain_profile@category:t /film:/freebase/domain_profile $0))"))
        questionWriter.println("(lambda $0 /common/topic (/freebase/domain_profile@category:t /m/010s:/freebase/domain_profile $0))");
      else if (line.equals("(count $0 (exists $1 (exists $2 (/soccer/football_league_participation@team@league@from@to:t $0 /en/uefa:/soccer/football_league $1 $2))))"))
        questionWriter.println("(count $0 (exists $1 (exists $2 (/sports/sports_league_participation@team@league@from@to:t $0 /en/uefa:/soccer/football_league $1 $2))))");
      else if (line.equals("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/tv/tv_regular_personal_appearance@to@from@appearance_type@person@program@seasons:t $1 $2 /en/newscaster:/tv/non_character_role $0 /en/abc_news:/tv/tv_program $3)))))"))
        questionWriter.println("(lambda $0 /common/topic (exists $1 (exists $2 (exists $3 (/business/employment_tenure@person@company@title:t $0 /en/abc_news:/tv/tv_program $1 /en/news_presenter:/tv/non_character_role $2)))))");
      else if (line.equals("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (/organization/leadership@person@title@as_of_date@organization@role@from@to:t $0 /en/chief_executive_officer:/type/text $1 /en/apple_inc:/organization/organization $2 $3 $4))))))"))
        questionWriter.println("(lambda $0 /people/person (exists $1 (exists $2 (exists $3 (exists $4 (/organization/leadership@person@title@as_of_date@organization@role@from@to:t $0 $1 $2 /en/apple_inc:/organization/organization $3 /en/chief_executive_officer:/type/text $4 $5))))))");
      else if (line.contains("/en/the_nutty_professor")) {
        String replaceLine = line.replace("/en/the_nutty_professor", "/en/the_nutty_professor_1996");
        questionWriter.println(replaceLine);
      } else if (line.contains("/base/boxing")) {
        String replaceLine = line.replace("/base/boxing", "/boxing");
        questionWriter.println(replaceLine);
      } else questionWriter.println(line);
    }
    questionWriter.close();

    PrintWriter npWriter = IOUtils.getPrintWriter(outNpFile);
    for (String line : IOUtils.readLines(inNpFile)) {
      if (line.equals("firefly :- NP : /base/ranker/rankerurlname/firefly$002F143400:/tv/tv_program"))
        npWriter.println("firefly :- NP : /m/014v3t:/tv/tv_program");
      else if (line.equals("manhattan bridge :- NP : /en/suspension_bridge:/transportation/bridge"))
        npWriter.println("manhattan bridge :- NP : /en/manhattan_bridge:/transportation/bridge");
      else if (line.equals("the beastie boys :- NP : /m/0116j8:/music/track"))
        npWriter.println("the beastie boys :- NP : /m/0116j8:/music/recording");
      else if (line.equals("sabotage :- NP : /m/0l16j8:/music/track"))
        npWriter.println("sabotage :- NP : /m/0l16j8:/music/recording");
      else if (line.equals("travels with my cello :- NP : /m/03crp32:/book/written_work"))
        npWriter.println("travels with my cello :- NP : /m/067y_k7:/book/written_work");
      else if (line.equals("film actor :- NP : /film/film_actor:/freebase/type_profile"))
        npWriter.println("film actor :- NP : /film/actor:/freebase/type_profile");
      else if (line.equals("invertigo :- NP : /m/03mfjrv:/amusement_parks/ride"))
        npWriter.println("invertigo :- NP : /m/0flmt0:/amusement_parks/ride");
      else if (line.equals("ceratopsia :- NP : /en/ceratopsia/-/base/dinosaur:/base/dinosaur/dinosaur"))
        npWriter.println("ceratopsia :- NP : /en/ceratopsia:/base/dinosaur/dinosaur");
      else if (line.equals("barosaurus :- NP : /en/barosaurus/-/base/dinosaur:/base/dinosaur/dinosaur"))
        npWriter.println("barosaurus :- NP : /en/barosaurus:/base/dinosaur/dinosaur");
      else if (line.equals("mercury :- NP : /authority/us/gov/hhs/fda/srs-unii/FXS1BY2PGL:/chemistry/chemical_element"))
        npWriter.println("mercury :- NP : /m/025sw5g:/chemistry/chemical_element");
      else if (line.equals("knickerbockers :- NP : /fashion/garment:/fashion/garment"))
        npWriter.println("knickerbockers :- NP : /en/knickerbockers:/fashion/garment");
      else if (line.equals("yahoo! :- NP : /business/cik/0001011006:/organization/organization"))
        npWriter.println("yahoo! :- NP : /m/019rl6:/organization/organization");
      else if (line.equals("uss alabama :- NP : /base/usnris/item/86000083:/location/location"))
        npWriter.println("uss alabama :- NP : /m/019zhn:/location/location");
      else if (line.equals("lohengrin :- NP : /base/imslp/65847:/opera/opera"))
        npWriter.println("lohengrin :- NP : /m/09hvx:/opera/opera");
      else if (line.equals("alice in wonderland :- NP : /source/allocine/fr/film/132663:/film/film"))
        npWriter.println("alice in wonderland :- NP : /m/04jpg2p:/film/film");
      else if (line.equals("bauhaus :- NP : /en/bauhaus:/architecture/architectural_style"))
        npWriter.println("bauhaus :- NP : /en/international_style:/architecture/architectural_style");
      else if (line.equals("polaris :- NP : /en/polaris:/astronomy/star"))
        npWriter.println("polaris :- NP : /m/0kjyrc7:/astronomy/star");
      else if (line.equals("mitchell public library :- NP : /en/mitchell_public_library:/library/public_library"))
        npWriter.println("mitchell public library :- NP : /m/0j9by57:/library/public_library");
      else if (line.equals("gold :- NP : /quotationsbook/subject/gold:/chemistry/chemical_element"))
        npWriter.println("gold :- NP : /m/025rs2z:/chemistry/chemical_element");
      else if (line.equals("nutty professor :- NP : /en/the_nutty_professor:/film/film"))
        npWriter.println("nutty professor :- NP : /en/the_nutty_professor_1996:/film/film");
      else if (line.equals("film domain :- NP : /film:/freebase/domain_profile"))
        npWriter.println("film domain :- NP : /m/010s:/freebase/domain_profile");
      else if (line.equals("newscaster :- NP : /en/newscaster:/tv/non_character_role"))
        npWriter.println("newscaster :- NP : /en/news_presenter:/tv/non_character_role");
      else if (line.matches("ali.*frazier ii :- NP : /en/ali-frazier_ii:/base/boxing/boxing_match")) {
      } else
        npWriter.println(line);
    }
    npWriter.println("the battle of the champions :- NP : /m/0kvlz:/boxing/boxing_match");
    npWriter.println("wba world champion :- NP : /m/0chgh2j:/boxing/boxing_title");
    npWriter.println("muhammad ali vs. joe frazier ii :- NP : /en/ali-frazier_ii:/boxing/boxing_match");
    npWriter.close();
  }

  // TODO - handle all entities that do not start with fb:m. or fb:en.
  private void convertExampleFile(String inFile, String outPrefix) throws IOException {

    PrintWriter formulaWriter = IOUtils.getPrintWriter(outPrefix + ".formulas");
    BufferedReader reader = IOUtils.getBufferedFileReader(inFile);
    List<String> examples = new ArrayList<String>();
    String line = reader.readLine();
    while (line != null) {
      Example example = new Example.Builder()
          .setUtterance(line)
          .setTargetFormula(processFree917LogicalForm(reader.readLine()))
          .createExample();
      line = reader.readLine();
      line = reader.readLine();
      examples.add(example.toJson());
      formulaWriter.println(example.targetFormula);
    }
    LogInfo.log("Arg count distribution: " + argnumCounter);
    reader.close();
    formulaWriter.close();


    int split = (int) (0.7 * examples.size());
    int[] perm = SampleUtils.samplePermutation(new Random(1), examples.size());
    List<String> train = new ArrayList<String>();
    List<String> test = new ArrayList<String>();
    for (int i = 0; i < split; i++)
      train.add(examples.get(perm[i]));
    for (int i = split; i < examples.size(); i++)
      test.add(examples.get(perm[i]));
    printToFile(outPrefix + ".train.examples", train);
    printToFile(outPrefix + ".test.examples", test);
    printToFile(outPrefix + ".examples", examples);
  }

  private void printToFile(String fileName, List<String> examples) throws IOException {
    PrintWriter exampleWriter = IOUtils.getPrintWriter(fileName);
    for (String example : examples) {
      exampleWriter.println(example);
    }
    exampleWriter.close();
  }

  private Formula processFree917LogicalForm(String free917LogicalForm) {

    LispTree tree = LispTree.proto.parseFromString(free917LogicalForm);
    if (tree.child(0).value.equals("lambda")) {
      // error check
      if (!tree.child(1).value.equals("$0") || tree.children.size() != 4)
        throw new RuntimeException("Illegal lambda expression: " + free917LogicalForm);
      return handleLambda(tree);
    } else if (tree.child(0).value.equals("count")) {
      if (!tree.child(1).value.equals("$0") || tree.children.size() != 3)
        throw new RuntimeException("Illegal lambda expression: " + free917LogicalForm);
      return handleCount(tree);
    } else if (tree.child(0).value.startsWith("/")) {
      return handleAsk(tree);
    } else
      throw new RuntimeException("Unknown free917 logical form: " + free917LogicalForm);
  }

  private Formula handleLambda(LispTree tree) {
    return handleBody(tree.child(3));
  }

  private Formula handleCount(LispTree tree) {
    Formula formula = handleBody(tree.child(2));
    if (formula == null)
      return null;
    return new AggregateFormula(AggregateFormula.Mode.count, formula);
  }

  private Formula handleBody(LispTree tree) {
    Map<String, String> argToPredMap = new HashMap<String, String>();
    handleBodyRecurse(tree, argToPredMap);
    return generateFormula(argToPredMap);
  }

  private Formula generateFormula(Map<String, String> argToPredMap) {

    if (argToPredMap.size() == 1) {
      String arg = argToPredMap.keySet().iterator().next();
      String pred = argToPredMap.get(arg);

      BinaryFormulaInfo info = formulaInfo.getBinaryInfo(Formulas.fromLispTree(LispTree.proto.parseFromString(pred)));
      if (info != null) {
        String type = formulaInfo.getBinaryInfo(Formulas.fromLispTree(LispTree.proto.parseFromString(pred))).expectedType1;
        if (cvts.contains(type)) {
          return fixCvtFormulas(pred, arg);
        }
      }

      return new JoinFormula(pred, getArgFormula(arg));
    } else {
      if (argToPredMap.get("target") == null)
        throw new RuntimeException("target is null: " + argToPredMap);

      Formula argsFormula = conjunctArgs(argToPredMap);
      Formula targetFormula = Formulas.reverseFormula(
          new ValueFormula<NameValue>(new NameValue(argToPredMap.get("target"))));
      Formula res = new JoinFormula(targetFormula, argsFormula);
      return res;
    }
  }

  private Formula fixCvtFormulas(String pred, String arg) {

    Formula join = new JoinFormula(pred, getArgFormula(arg));
    if (pred.equals("!fb:automotive.trim_level.msrp") || pred.equals("!fb:event.disaster.damage") ||
        pred.equals("!fb:comic_books.comic_book_issue.cover_price")) {
      return new JoinFormula("!fb:measurement_unit.money_value.amount", join);
    } else if (pred.equals("!fb:celebrities.celebrity.net_worth") || pred.equals("!fb:projects.project.actual_cost")
        || pred.equals("!fb:digicams.digital_camera.street_price") || pred.equals("!fb:amusement_parks.ride.cost")) {
      return new JoinFormula("!fb:measurement_unit.dated_money_value.amount", join);
    } else if (pred.equals("!fb:computer.software.compatible_oses")) {
      return new JoinFormula("!fb:computer.software_compatibility.operating_system", join);
    } else if (pred.equals("!fb:finance.stock_exchange.companies_traded")) {
      return new JoinFormula("!fb:business.stock_ticker_symbol.ticker_symbol", join);
    } else if (pred.equals("!fb:medicine.hospital.beds") || pred.equals("!fb:metropolitan_transit.transit_system.daily_riders")
        || pred.equals("!fb:library.public_library_system.collection_size") || pred.equals("!fb:library.public_library_system.annual_visits")
        || pred.equals("!fb:protected_sites.protected_site.annual_visitors") || pred.equals("!fb:amusement_parks.park.annual_visits")
        || pred.equals("!fb:education.educational_institution.total_enrollment")
        || pred.equals("!fb:religion.religion.number_of_adherents")) {
      return new JoinFormula("!fb:measurement_unit.dated_integer.number", join);
    } else if (pred.equals("!fb:business.employer.employees")) {
      return new JoinFormula("!fb:business.employment_tenure.person", join);
    } else if (pred.equals("!fb:tv.tv_series_episode.producers")) {
      return new JoinFormula("!fb:tv.tv_producer_episode_credit.producer", join);
    } else if (pred.equals("!fb:book.periodical.frequency_or_issues_per_year")) {
      return new JoinFormula("!fb:book.periodical_frequency.issues_per_year", join);
    } else if (pred.equals("!fb:book.periodical.first_issue_date")) {
      return new JoinFormula("!fb:book.periodical_publication_date.date", join);
    } else if (pred.equals("!fb:games.game.number_of_players") || pred.equals("!fb:aviation.aircraft_model.passengers")) {
      return new JoinFormula("!fb:measurement_unit.integer_range.high_value", join);
    } else if (pred.equals("!fb:military.armed_force.personnel")) {
      return new JoinFormula("!fb:military.military_service.military_person", join);
    } else if (pred.equals("!fb:business.consumer_company.products")) {
      return new JoinFormula("!fb:business.company_product_relationship.consumer_product", join);
    } else if (pred.equals("!fb:location.location.geolocation")) {
      return new JoinFormula("!fb:location.geocode.longitude", join);
    }
    return new JoinFormula(pred, getArgFormula(arg));

  }

  private Formula conjunctArgs(Map<String, String> argToPredMap) {

    List<JoinFormula> pivots = new ArrayList<JoinFormula>();
    for (String arg : argToPredMap.keySet()) {
      if (!arg.equals("target"))
        pivots.add(constructJoin(arg, argToPredMap.get(arg)));
    }
    Formula res = pivots.get(0);
    if (pivots.size() == 1) return res;
    for (int i = 1; i < pivots.size(); ++i)
      res = new MergeFormula(Mode.and, res, pivots.get(i));
    return res;
  }

  private Formula getArgFormula(String arg) {
    if (arg.startsWith("fb:"))
      return new ValueFormula<NameValue>(new NameValue(arg));
    if (arg.startsWith("DATE::")) {
      String[] tokens = arg.split("::");
      return new ValueFormula<DateValue>(DateValue.parseDateValue(tokens[1]));
    }
    // TODO make sure ints and booleans work
    if (arg.startsWith("INT::")) {
      String[] tokens = arg.split("::");
      return new ValueFormula<NumberValue>(new NumberValue(Double.parseDouble(tokens[1]), NumberValue.unitless));
    }
    if (arg.startsWith("BOOL::")) {
      String[] tokens = arg.split("::");
      return new ValueFormula<NameValue>(new NameValue(tokens[1]));
    }
    if (arg.startsWith("TEXT::")) {
      String[] tokens = arg.split("::");
      return new ValueFormula<StringValue>(new StringValue(tokens[1]));
    }
    throw new RuntimeException("Unknown arg: " + arg);
  }

  private JoinFormula constructJoin(String arg, String pred) {
    VariableFormula var = new VariableFormula("x");
    JoinFormula join = new JoinFormula(pred, var);
    LambdaFormula lambda = new LambdaFormula("x", join);
    return new JoinFormula(lambda, getArgFormula(arg));
  }

  private void handleBodyRecurse(LispTree tree, Map<String, String> argToPredMap) {
    if (tree.child(0).value.equals("exists")) {
      if (tree.children.size() != 3)
        throw new RuntimeException("bad exists clause: " + tree);
      handleBodyRecurse(tree.child(2), argToPredMap);
    } else {
      if (!tree.child(0).value.startsWith("/"))
        throw new RuntimeException("bad exists clause: " + tree);

      // parse the relation

      String predicate = tree.child(0).value;
      String[] predTokens = predicate.substring(0, predicate.lastIndexOf(':')).split("@");
      if (predTokens.length <= 1)
        throw new RuntimeException("Bad body: " + tree);

      String fbType = constructFbType(predTokens[0]);
      List<String> fbRelations = constructFbRelations(predTokens, fbType);

      // parse the arguments
      if (predTokens.length == 2) {
        if (tree.child(1).value.equals("$0"))
          argToPredMap.put(parseEntity(tree.child(2).value), fbRelations.get(0));
        else if (tree.child(2).value.equals("$0"))
          argToPredMap.put(parseEntity(tree.child(1).value), "!" + fbRelations.get(0));
        else throw new RuntimeException("bad non-cvt tree: " + tree);
      } else {
        List<String> fbArguments = constructFbArguments(tree);
        for (int i = 0; i < fbArguments.size(); ++i) {
          String fbRelation = fbRelations.get(i);
          if (fbArguments.get(i).equals("$0"))
            argToPredMap.put("target", fbRelation);
          else if (!fbArguments.get(i).startsWith("$")) {
            argToPredMap.put(fbArguments.get(i), fbRelation);
          }
        }
      }
      argnumCounter.incrementCount(argToPredMap.size());
    }
  }

  private List<String> constructFbArguments(LispTree tree) {
    boolean lastName = false;
    List<String> res = new ArrayList<String>();
    for (int j = 1; j < tree.children.size(); ++j) {

      if (tree.child(j).value.equals("$0")) {
        res.add("$0");
        lastName = false;
      } else if (tree.child(j).value.startsWith("/")) {
        String entity = parseEntity(tree.child(j).value);
        res.add(entity);
        lastName = true;
      } else {
        if (!lastName)
          res.add(tree.child(j).value);
        lastName = false;
      }
    }
    return res;
  }

  private List<String> constructFbRelations(String[] predTokens, String fbType) {

    List<String> res = new ArrayList<String>();
    if (predTokens[0].contains("&")) {
      String relation = "!" + FormatConverter.fromSlashToDot(predTokens[0].substring(0, predTokens[0].indexOf('&')), true);
      res.add(relation);
    }
    for (int j = 1; j < predTokens.length; ++j)
      res.add(fbType + "." + predTokens[j]);
    return res;
  }

  private String constructFbType(String predHead) {
    if (predHead.contains("&"))
      return FormatConverter.fromSlashToDot(predHead.substring(predHead.indexOf('&') + 1), true);
    return FormatConverter.fromSlashToDot(predHead, true);

  }

  private String parseEntity(String value) {

    if (value.startsWith("/type/datetime/")) {
      String[] tokens = value.split(":");
      String date = tokens[0].substring(tokens[0].lastIndexOf('/') + 1);
      return "DATE::" + date;
    } else if (value.startsWith("/type/boolean/")) {
      String[] tokens = value.split(":");
      String b = tokens[0].substring(tokens[0].lastIndexOf('/') + 1);
      return "BOOL::" + b;
    } else if (value.startsWith("/type/int/")) {
      String[] tokens = value.split(":");
      String i = tokens[0].substring(tokens[0].lastIndexOf('/') + 1);
      return "INT::" + i;
    } else if (value.startsWith("/type/text/")) {
      String[] tokens = value.split(":");
      String i = tokens[0].substring(tokens[0].lastIndexOf('/') + 1);
      return "TEXT::" + i;
    }
    return FormatConverter.fromSlashToDot(value.substring(0, value.indexOf(':')), true);
  }

  /** There is one example and this method is tailored for that */
  private Formula handleAsk(LispTree tree) {
    String pred = tree.child(0).value;
    ValueFormula<NameValue> arg1 = Formulas.newNameFormula(parseEntity(tree.child(1).value));
    ValueFormula<NameValue> arg2 = Formulas.newNameFormula(parseEntity(tree.child(2).value));
    pred = pred.replace('@', '/');
    pred = pred.substring(0, pred.lastIndexOf(':'));
    pred = FormatConverter.fromSlashToDot(pred, true);
    ValueFormula<NameValue> predFormula = Formulas.newNameFormula(pred);
    JoinFormula pf = new JoinFormula(predFormula, arg2);
    MergeFormula mf = new MergeFormula(Mode.and, arg1, pf);
    return mf;
  }

  public void genreateEntityInfoFile(String free917EntityFile, String entityInfoFile, String outFile, String missingEntitiesFile) throws IOException {

    Map<String, String> midToIdMap = FileUtils.loadStringToStringMap(midToIdFile);

    Map<String, Set<String>> idToNameMap = new HashMap<String, Set<String>>();
    for (String line : IOUtils.readLines(free917EntityFile)) {

      String[] tokens = line.split(":-");
      String name = tokens[0].trim().replace('-', ' ');
      name = name.replace("#", "# ");
      name = name.replace("!", " !");
      name = name.replace("'", " '");
      name = name.replace(",", " ,");
      name = name.replace(":", " :");
      String entry = tokens[1].trim();
      String[] entryTokens = entry.split(":");
      String id = entryTokens[1].trim();
      id = FormatConverter.fromSlashToDot(id, true);

      if (midToIdMap.containsKey(id))
        id = midToIdMap.get(id);

      MapUtils.add(idToNameMap, id, name);
    }
    LogInfo.log("Number of entries: " + idToNameMap.size());

    PrintWriter writer = IOUtils.getPrintWriter(outFile);
    int i = 0;
    for (String line : IOUtils.readLines(entityInfoFile)) {
      String[] tokens = line.split("\t");
      String id = tokens[1];

      if (idToNameMap.containsKey(id)) {

        if (idToNameMap.get(id).size() > 1)
          System.out.println("Multiple names: " + idToNameMap.get(id));
        for (String name : idToNameMap.get(id)) {
          if (name.equals("beer")) {
            tokens[3] = "beer";
            writer.println(StringUtils.join(tokens, "\t"));
            tokens[3] = "beers";
            writer.println(StringUtils.join(tokens, "\t"));
          } else if (name.equals("film actor")) {
            tokens[3] = "film actor";
            writer.println(StringUtils.join(tokens, "\t"));
            tokens[3] = "film actors";
            writer.println(StringUtils.join(tokens, "\t"));
          } else {
            tokens[3] = name;
            writer.println(StringUtils.join(tokens, "\t"));
          }
        }
        idToNameMap.remove(id);
      }
      if (i % 1000000 == 0)
        System.out.println("Lines: " + i++);
      i++;
    }
    writer.close();
    PrintWriter missingWriter = IOUtils.getPrintWriter(missingEntitiesFile);
    for (String id : idToNameMap.keySet()) {
      if (id.startsWith("fb:type") || id.startsWith("fb:un."))
        continue;
      missingWriter.println(id + "\t" + idToNameMap.get(id));
    }
    missingWriter.close();
  }
}
