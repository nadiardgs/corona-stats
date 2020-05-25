package br.com.coronastats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPSClient;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class Main {

	private final static String url = "https://especiais.g1.globo.com/bemestar/coronavirus/mapa-coronavirus/#/";

	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final Charset ISO = Charset.forName("ISO-8859-1");
	
	private static String citiesPopulationRemotePath 	= "/MyDocuments/cidadesXPopulacao.txt";
	private static String stateAcronymRemotePath 	  	= "/MyDocuments/EstadosxSigla.txt";
	
	private static String server;
	private static String username;
	private static String password;
	private static Integer port;

	public static int indexOf(String str, String substr, int n) {
		int p = str.indexOf(substr);
		while (--n > 0 && p != -1) {
			p = str.indexOf(substr, p + 1);
		}
		return p;
	}
	
	public static String getTodayDate() {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDateTime now = LocalDateTime.now();
		return dtf.format(now);
	}
	
	public static Double calculatePercentage(Integer total, Integer totalPop)
	{
		Double finalInfectedPercentage = (double) total * 100 / (double) totalPop;
		return BigDecimal.valueOf(finalInfectedPercentage).setScale(3, RoundingMode.HALF_UP).doubleValue();
	}
	
	public static String writeLine(String city, Integer numberInfected, Integer numberPopulation, Integer numberDeceased, 
									Double percentageInfected, Double percentageDeceased)
	{
		String stringDeceased = numberDeceased == 1 ? " morto. " : " mortos. ";
		
		return city + ": " + numberInfected + " de " + numberPopulation + 
				" habitantes infectados, com " + numberDeceased + stringDeceased + "Percentagem de infectados: " + percentageInfected
				+ "%; Mortalidade: " + percentageDeceased + "%.\n";
	}
	
	public static Integer getTotalByState(Map<String, Integer> map, String state)
	{
		return map.entrySet().stream()
				.filter(list -> list.getKey().contains(", " + state.substring(state.indexOf("\t")).trim()))
				.map(list -> list.getValue()).collect(Collectors.summingInt(Integer::intValue));
	}
	
	public static File generateAnalytics(Map<String, Integer> listInfected, Map<String, Integer> listDeceased, Map<String, Integer> totalPopulation)
			throws IOException {
		File file = new File(returnFilename("Analysis.txt"));
		
		Integer infectedValue;
		
		OutputStreamWriter bf = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.ISO_8859_1);
		
		//comparing strings ignoring special characters from ISO_8859_1
		Collator collator = Collator.getInstance (new Locale ("pt", "BR"));
		collator.setStrength(Collator.PRIMARY);
		
		try {
			Double percentageInfected, percentageDeceased;
			List<String> lInfected = new ArrayList<String>(listInfected.keySet());
			
			List<String> lTotalPopulation = new ArrayList<String>(totalPopulation.keySet());
			
			List<String> lstStateAcronym = generateListFromConnection(server, username, password, stateAcronymRemotePath);
			
			Integer totalInfected = listInfected.values().stream().mapToInt(Integer::intValue).sum();
			Integer totalDeceased = listDeceased.values().stream().mapToInt(Integer::intValue).sum();
			Integer totalPop = totalPopulation.values().stream().mapToInt(Integer::intValue).sum();
			Integer numberDeceased;
			
			Double finalInfectedPercentage = calculatePercentage(totalInfected, totalPop);
			Double finalDeceasedPercentage = calculatePercentage(totalDeceased, totalInfected);
			
			//it isn't a city, I don't need it anymore
			lInfected.remove("Não informado");
			
			List<String> citiesNotFound = new ArrayList<String>();
			
			Iterator<String> itInfected = lInfected.listIterator();
			while (itInfected.hasNext())
			{
				String s = itInfected.next();
				//if a city with infected population can't be found in the list of cities, then we have a problem
				
				if (!lTotalPopulation.contains(s))
				{
					//detecting cities that the web page mistakenly attributed to the wrong state
					String cityName = s.substring(0, s.indexOf(","));
					String state = s.substring(s.indexOf(",")+2).trim();
					
					String cityTotalPopulation = lTotalPopulation.stream()
							  .filter(cityList -> collator.compare(cityName, cityList.substring(0, cityList.indexOf((",")))) == 0)
							 // cityName.equalsIgnoreCase(cityList.substring(0, cityList.indexOf((",")))))
							  .findAny()
							  .orElse(null);
					if (cityTotalPopulation != null)
					{
						String totPopState = cityTotalPopulation.substring(cityTotalPopulation.indexOf(",")+2).trim();
						
						itInfected.remove(); //remove wrong city + state from list
						citiesNotFound.add(cityTotalPopulation);
							
						//find value with the wrong key in the map, delete it, and add the value with the right key
						infectedValue = listInfected.get(s);
						listInfected.remove(s);
						listInfected.put(cityTotalPopulation, infectedValue);
						
						if (!state.equalsIgnoreCase(totPopState)) //only shows the warning if the states are different
							System.out.println("Cidade " + s + " foi atribuida ao estado errado pela pagina da Web. Valor corrigido: " + cityTotalPopulation + ".");
						
					}
					else //if city still couldn't be found, then we really have a problem
						System.out.println("Cidade " + s + " nao encontrada na lista de cidades do Brasil. Favor verificar.");
				}
			}
			
			for (String city : citiesNotFound) {
				lInfected.add(city); }
			
			//now, sort list of infected
			Collections.sort(lInfected);
			
			String line = writeLine("Brasil", totalInfected, totalPop, totalDeceased, 
					finalInfectedPercentage, finalDeceasedPercentage);
			bf.write(line + "\n");
			System.out.println(line);
			
			//generate statistics per state
			for (String state : lstStateAcronym)
			{
				Integer totalInfPerState = getTotalByState(listInfected, state);
				Integer totalDecPerState = getTotalByState(listDeceased, state);	
				Integer totalPopPerState = getTotalByState(totalPopulation, state);
				
				percentageInfected = calculatePercentage(totalInfPerState, totalPopPerState);
				percentageDeceased = calculatePercentage(totalDecPerState, totalInfPerState);
					
				line = writeLine(state.substring(0, state.indexOf("\t")), totalInfPerState, totalPopPerState, totalDecPerState, 
						percentageInfected, percentageDeceased);
				bf.write(line);
			}
			
			bf.write("\n");
			
			for (String l : lInfected) {
				for (String tp : lTotalPopulation) {
					if (collator.compare(l, tp) == 0) {
						if (listInfected.get(tp) != null && totalPopulation.get(tp) != null)
						{
							percentageInfected = calculatePercentage(listInfected.get(tp), totalPopulation.get(tp));
						
							numberDeceased = (listDeceased.get(tp) == null) ? 0 : listDeceased.get(tp);  
							percentageDeceased = calculatePercentage(numberDeceased, listInfected.get(tp));
						
							line = writeLine(tp, listInfected.get(tp), totalPopulation.get(tp), numberDeceased, 
									percentageInfected, percentageDeceased);
							bf.write(line);
						}
					}
				}
			}
			

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		finally
		{
			bf.close();
		}
		return file;
	}

	// reads the File created by the method getWebPageData and returns a Map populated with the desired data
	
	/*2020-04-17: today the website changed its data format: it shows both cases and deaths per city.
	 * I'll have to change the whole logic  */ 
	public static Map<String, Integer> listInfectedToday(File file) throws IOException {
		
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String line;
		String[] cities = null;

		try {
			while ((line = br.readLine()) != null) {
				if (line.contains("places__body")) {
					cities = line.split("<li class=\"places__item");
				}	
			}

			List<String> list = Arrays.asList(cities);
			String cityName;
			String cityInfectedString;
			Integer cityInfected;

			Map<String, Integer> citiesXCases = new HashMap<String, Integer>();

			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).contains(". ")) {
					cityInfectedString = list.get(i).substring(indexOf(list.get(i), ">", 4) + 1, indexOf(list.get(i), "<", 4)).replace(".", "");
					if (!cityInfectedString.isEmpty())
					{
						cityName = list.get(i).substring(list.get(i).indexOf(".") + 2, indexOf(list.get(i), "<", 2));
						cityInfected = Integer.parseInt(cityInfectedString);
						if (cityInfected != 0) //the data brings some cities with 0 cases, I added this line to avoid them
							citiesXCases.put(cityName, cityInfected);
					}
				}
			}

			br.close();

			if (!citiesXCases.isEmpty())
				return citiesXCases;

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
	
	//this method returns a List<String> with the data of the specified FTP file
	public static List<String> generateListFromConnection(String server, String username, String password, String remotePath) throws IOException
	{
		FTPSClient ftp = new FTPSClient();
		List<String> lstFTP = null; 
		try
		{
			ftp.connect(server, 21);
			try
			{
				ftp.login(username, password);
				System.out.println("Login realizado com sucesso");
			}
			
			catch (Exception e)
			{
				System.out.println("Erro ao realizar login. " + e.getMessage() + ": " + e.getCause());
			}
			
			ftp.enterLocalPassiveMode();
			
			InputStream is = ftp.retrieveFileStream(remotePath);
			BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF_8));
			lstFTP = new ArrayList<String>();
			lstFTP = br.lines().collect(Collectors.toList());
			lstFTP.removeAll(Arrays.asList("", null));
			
			is.close();
			br.close();
			ftp.logout();
			ftp.disconnect();
		}
		
		catch (Exception e)
		{
			System.out.println("Erro ao recuperar arquivo " + remotePath + ": " + e.getMessage());
		}
		
		return lstFTP;
	}
	
	//this method returns a map with the name of the city + state acronym as key, and the population as value
	public static Map<String, Integer> readTotalPopulationFromFTP(String citiesPopulationRemotePath, String stateAcronymRemotePath) {
		Map<String, Integer> mapTotalPopulation = new HashMap<String, Integer>();
		try {
			
			String cityName, state, acronym;
			Integer cityPopulation;
			
			/* formatted like: state "\t" acronym
			 * for example: Sao Paulo "\t" SP */
			List<String> lstStateAcronym = generateListFromConnection(server, username, password, stateAcronymRemotePath);
			
			/* formatted like: city "\t" state "\t" population
			 * for example: Campinas "\t" Sao Paulo "\t" 4459347 */
			List<String> lstCitiesPopulation = generateListFromConnection(server, username, password, citiesPopulationRemotePath);
			
			for (String cityPop : lstCitiesPopulation)
			{
				cityName = cityPop.substring(0, cityPop.indexOf("\t")).trim();
				state = cityPop.substring(cityPop.indexOf("\t"), indexOf(cityPop, "\t", 2)).trim();
				cityPopulation = Integer.parseInt(cityPop.substring(indexOf(cityPop, "\t", 2)).trim());
				
				for (String stateAcr : lstStateAcronym)
				{
					acronym = stateAcr.substring(stateAcr.indexOf("\t")).trim();
					if (stateAcr.equalsIgnoreCase(state + "\t" + acronym))
						mapTotalPopulation.put(cityName + ", " + acronym, cityPopulation);
				}
			}
		}

		catch (Exception e) {
			e.printStackTrace();
		}
		
		return mapTotalPopulation;
	}
	
	public static void sendFileToFTP(File file, String server, Integer port, String username, String password) throws IOException
	{
		FTPSClient ftp = new FTPSClient();
		try {
			ftp.connect(server, port);
			ftp.login(username, password);
			ftp.enterLocalPassiveMode();

			InputStream is = new FileInputStream(file);

			ftp.setFileType(FTP.BINARY_FILE_TYPE);

			ftp.storeFile(file.getName(), is);
			boolean completed = ftp.completePendingCommand();
			if (completed) {
				System.out.println("Upload de arquivo realizado com sucesso");
			}
			
			String[] ftpFiles = ftp.listNames();
			List<String> lstFiles = Arrays.asList(ftpFiles);
			lstFiles.forEach(System.out::println);
			
			System.out.println(file.getName());
			if (lstFiles.contains(file.getName()))
			{
				System.out.println("Upload de arquivo realizado com sucesso");
				return;
			}
		
			ftp.logout();
			is.close();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			ftp.logout();

		} catch (FTPConnectionClosedException ftpExc) {
			ftp.logout();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		finally {
			try {
				if (ftp.isConnected()) {
					ftp.logout();
					ftp.disconnect();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public static File saveFile(WebDriver driver, String filename)
	{
		File file = new File(filename);
		try {
			
			BufferedWriter bf = new BufferedWriter(new FileWriter(file));
			bf.write(new String(driver.getPageSource().getBytes(ISO)));
			bf.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return file;
	}
	
	public static String returnFilename(String filename)
	{
		return System.getProperty("user.home") + System.getProperty("file.separator") + getTodayDate() + filename;
	}
	
	public static List<File> getWebPageData(String url) throws Exception {
		
		if (System.getProperty("os.name").toLowerCase().contains("win"))
			System.setProperty("webdriver.chrome.driver", System.getProperty("user.dir") + System.getProperty("file.separator") + "chromedriver.exe");
		else
			System.setProperty("webdriver.chrome.driver", System.getProperty("user.dir") + System.getProperty("file.separator") + "chromedriver");
		
		WebDriver driver = new ChromeDriver();
		driver.get(url);
		
		//now the page shows by default the number of deaths
		File deaths = saveFile(driver, returnFilename("Deaths.txt"));
		
		//clicks the element that shows number of cases
		driver.findElement(By.cssSelector(".cases-overview.cases-overview--cases")).click();
		
		//and saves the file with the web content that now shows number of cases
		File cases = saveFile(driver, returnFilename("Cases.txt"));
		
		List<File> lstFiles = new ArrayList<File>();
		lstFiles.add(deaths);
		lstFiles.add(cases);
		
		driver.quit();
		
		return lstFiles;
    }
	
	public static void main(String[] args) throws Exception {
		
		Properties prop = new Properties();
		prop.load(new FileInputStream(System.getProperty("user.home") + System.getProperty("file.separator") + "data.properties"));
		
		server = prop.getProperty("server").trim();
		port = Integer.parseInt(prop.getProperty("port"));
		username = prop.getProperty("username").trim();
		password = prop.getProperty("password").trim();
		
		System.out.println("Iniciando carga de arquivos");
		  
		List<File> listFiles = getWebPageData(url);
		
		Map<String, Integer> deceasedToday = listInfectedToday(listFiles.get(0));
		Map<String, Integer> infectedToday = listInfectedToday(listFiles.get(1));
		
		for (File f : listFiles)
		{
			f.delete();
		}
		
		//if data can't be read from web page, abort program
		if (deceasedToday.isEmpty() || infectedToday.isEmpty())
		{
			System.out.println("Nao foi possivel extrair dados da pagina da Web. Verifique se a mesma esta disponivel.");
			return;
		}
		
		Map<String, Integer> totalPopulation = readTotalPopulationFromFTP(citiesPopulationRemotePath, stateAcronymRemotePath); 
		
		if (totalPopulation == null || infectedToday == null || deceasedToday == null) 
		{
			System.out.print("Nao foi possivel analisar os dados extraidos da pagina da Web. Verifique o log."); 
			return;
		}
		  
		System.out.println("Gerando analise dos dados..."); 
		
		File finalFile = generateAnalytics(infectedToday, deceasedToday, totalPopulation); 
		sendFileToFTP(finalFile, server, port, username, password);
	}
}
