package it.unibas.lunatic.persistence;

import it.unibas.lunatic.persistence.encoding.DictionaryEncoder;
import it.unibas.lunatic.LunaticConfiguration;
import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.exceptions.DAOException;
import it.unibas.lunatic.model.chase.commons.ChaseStats;
import it.unibas.lunatic.model.dependency.RewriteSTTGDs;
import it.unibas.lunatic.model.dependency.operators.ProcessDependencies;
import it.unibas.lunatic.parser.ParserOutput;
import it.unibas.lunatic.parser.operators.ParseDependencies;
import java.util.Date;
import java.util.List;
import org.jdom.Document;
import org.jdom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import speedy.model.database.IDatabase;
import speedy.model.database.dbms.SQLQueryString;
import speedy.persistence.xml.DAOXmlUtility;

public class DAOMCScenarioStandard {

    private static Logger logger = LoggerFactory.getLogger(DAOMCScenarioStandard.class);
    private final DAOXmlUtility daoUtility = new DAOXmlUtility();
    private final DAOLunaticConfiguration daoConfiguration = new DAOLunaticConfiguration();
    private final DAODatabaseConfiguration daoDatabaseConfiguration = new DAODatabaseConfiguration();
    private final ProcessDependencies dependencyProcessor = new ProcessDependencies();

    public Scenario loadScenario(String fileScenario, String suffix) throws DAOException {
        long start = new Date().getTime();
        try {
            Scenario scenario = new Scenario(fileScenario, suffix);
            long startLoadXML = new Date().getTime();
            Document document = daoUtility.buildDOM(fileScenario);
            long endLoadXML = new Date().getTime();
            ChaseStats.getInstance().addStat(ChaseStats.LOAD_XML_SCENARIO_TIME, endLoadXML - startLoadXML);
            Element rootElement = document.getRootElement();
            //CONFIGURATION
            Element configurationElement = rootElement.getChild("configuration");
            LunaticConfiguration configuration = daoConfiguration.loadConfiguration(configurationElement);
            scenario.setConfiguration(configuration);
            if (configuration.isUseDictionaryEncoding()) {
                scenario.setValueEncoder(new DictionaryEncoder(DAOUtility.extractScenarioName(fileScenario)));
                scenario.getValueEncoder().prepareForEncoding();
            }
            //SOURCE
            Element sourceElement = rootElement.getChild("source");
            IDatabase sourceDatabase = daoDatabaseConfiguration.loadDatabase(sourceElement, null, fileScenario, scenario.getValueEncoder()); //Source schema doesn't need suffix
            scenario.setSource(sourceDatabase);
            //TARGET
            Element targetElement = rootElement.getChild("target");
            IDatabase targetDatabase = daoDatabaseConfiguration.loadDatabase(targetElement, suffix, fileScenario, scenario.getValueEncoder());
            scenario.setTarget(targetDatabase);
            long end = new Date().getTime();
            ChaseStats.getInstance().addStat(ChaseStats.LOAD_TIME, end - start);
            //InitDB (out of LOAD_TIME stat)
            daoDatabaseConfiguration.initDatabase(scenario);
            start = new Date().getTime();
            //AUTHORITATIVE SOURCES
            Element authoritativeSourcesElement = rootElement.getChild("authoritativeSources");
            List<String> authoritativeSources = daoDatabaseConfiguration.loadAuthoritativeSources(authoritativeSourcesElement, scenario);
            scenario.setAuthoritativeSources(authoritativeSources);
            //DEPENDENCIES
            Element dependenciesElement = rootElement.getChild("dependencies");
            loadDependencies(dependenciesElement, scenario);
            //CONFIGURATION
            daoConfiguration.loadOtherScenarioElements(rootElement, scenario);
            //QUERIES
            Element queriesElement = rootElement.getChild("queries");
            loadQueries(queriesElement, scenario);
            end = new Date().getTime();
            ChaseStats.getInstance().addStat(ChaseStats.LOAD_TIME, end - start);
            if (configuration.isUseDictionaryEncoding()) {
                scenario.getValueEncoder().closeEncoding();
            }
            return scenario;
        } catch (Throwable ex) {
            logger.error(ex.getLocalizedMessage());
            ex.printStackTrace();
            String message = "Unable to load scenario from file " + fileScenario;
            if (ex.getMessage() != null && !ex.getMessage().equals("NULL")) {
                message += "\n" + ex.getMessage();
            }
            throw new DAOException(message);
        } finally {
        }
    }

    private void loadDependencies(Element dependenciesElement, Scenario scenario) throws DAOException {
        if (dependenciesElement == null) {
            return;
        }
        String dependenciesString = dependenciesElement.getValue().trim();
        loadDependencies(dependenciesString, scenario);
    }

    public void loadDependencies(String dependenciesString, Scenario scenario) throws DAOException {
        ParseDependencies generator = new ParseDependencies();
        try {
            long start = new Date().getTime();
            generator.generateDependencies(dependenciesString, scenario);
            ParserOutput parserOutput = generator.getParserOutput();
            dependencyProcessor.processDependencies(parserOutput, scenario);
            long end = new Date().getTime();
            ChaseStats.getInstance().addStat(ChaseStats.PARSING_TIME, end - start);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new DAOException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadQueries(Element queriesElement, Scenario scenario) {
        if (queriesElement == null) {
            return;
        }
        List<Element> sourceElements = queriesElement.getChildren("query");
        for (Element sourceElement : sourceElements) {
            String queryId = sourceElement.getAttributeValue("id");
            String queryString = sourceElement.getText();
            SQLQueryString sqlQueryString = new SQLQueryString(queryId, queryString);
            scenario.addSQLQueryString(sqlQueryString);
        }
    }

}
