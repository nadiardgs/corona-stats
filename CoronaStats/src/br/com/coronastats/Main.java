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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.WebDriver;

public class Main {

	private final static String url = "https://especiais.g1.globo.com/bemestar/coronavirus/mapa-coronavirus/";

	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final Charset ISO = Charset.forName("ISO-8859-1");

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

	
	
	public static File generateAnalytics(Map<String, Integer> listInfected, Map<String, Integer> totalPopulation)
			throws IOException {
		String todayDate = getTodayDate();
		File file = new File(System.getProperty("user.home") + System.getProperty("file.separator") + todayDate + "Analysis.txt");
		
		OutputStreamWriter bf = new OutputStreamWriter(new FileOutputStream(
				file),
				StandardCharsets.ISO_8859_1);
		
		try {
			Double percentage;

			List<String> lInfected = new ArrayList<String>(listInfected.keySet());
			Integer totalInfected = listInfected.values().stream().mapToInt(Integer::intValue).sum();
			
			Collections.sort(lInfected);
			List<String> lTotalPopulation = new ArrayList<String>(totalPopulation.keySet());
			Integer totalPop = totalPopulation.values().stream().mapToInt(Integer::intValue).sum();
			
			Double finalPercentage = (double) totalInfected * 100 / (double) totalPop;
			finalPercentage = BigDecimal.valueOf(finalPercentage).setScale(3, RoundingMode.HALF_UP).doubleValue();
			
			bf.write("Brasil: " + totalInfected + " de " + totalPop + " habitantes infectados. Percentagem de contágio: "
					+ finalPercentage + "\n");
			
			for (String l : lInfected) {
				for (String tp : lTotalPopulation) {
					
					if (l.equalsIgnoreCase(tp)) {
						percentage = ((double) listInfected.get(tp) * 100 / (double) totalPopulation.get(tp));

						Double truncatedDouble = BigDecimal.valueOf(percentage).setScale(3, RoundingMode.HALF_UP)
								.doubleValue();
						
						bf.write(tp + ": " + listInfected.get(tp) + " de " + totalPopulation.get(tp) + 
								" habitantes infectados. Percentagem de contágio: " + truncatedDouble);
						bf.write("\n");
						
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

	// edits the file (creates another file) created by the method getWebPageData, accepting the generated file as argument
	public static Map<String, Integer> listInfectedToday(File file) throws IOException {
		
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String line;
		String[] cities = null;

		String todayDate = getTodayDate();

		OutputStreamWriter bf = new OutputStreamWriter(
				new FileOutputStream(
						System.getProperty("user.home") + System.getProperty("file.separator") + todayDate + "InfectedToday.txt"),
				StandardCharsets.ISO_8859_1);

		try {
			while ((line = br.readLine()) != null) {
				if (line.contains("places__body")) {
					cities = line.split("<li class=\"places__item\">");
				}	
			}

			List<String> list = Arrays.asList(cities);
			String cityName;
			String cityInfectedString;
			Integer cityInfected;

			Map<String, Integer> citiesXCases = new HashMap<String, Integer>();

			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).contains(",")) {

					cityInfectedString = list.get(i).substring(indexOf(list.get(i), ">", 3) + 1, indexOf(list.get(i), "<", 4));
					if (!cityInfectedString.isEmpty())
					{
						cityName = list.get(i).substring(list.get(i).indexOf(">") + 1, list.get(i).indexOf(",")+4);
						cityInfected = Integer.parseInt(cityInfectedString);
						bf.write(cityName + "\t" + cityInfected);
						bf.write("\n");
						citiesXCases.put(cityName, cityInfected);
					}
				}
				else
				{
					if (list.get(i).contains("Alta Floresta D'Oeste"))
					{
						cityInfected = Integer.parseInt(list.get(i).substring(indexOf(list.get(i), ">", 3) + 1, indexOf(list.get(i), "<", 4)));
						cityName = "Não informado";
						bf.write(cityName + "\t" + cityInfected);
						bf.write("\n");
						citiesXCases.put(cityName, cityInfected);
					}
				}
			}

			br.close();
			bf.close();

			if (!citiesXCases.isEmpty())
				return citiesXCases;

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
	
	public static BufferedReader generateReaderFromConnection(String server, String username, String password, String remotePath) throws IOException
	{
		FTPClient ftp = new FTPClient();
		ftp.connect(server);
		ftp.login(username, password);
		
		InputStream is = ftp.retrieveFileStream(remotePath);
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		
		if (ftp.isConnected())
		{
			ftp.logout();
			ftp.disconnect();
		}
		return br;
	}
	
	
	public static Map<String, Integer> readTotalPopulationFromFTP(String server, String username, String password, 
																		String citiesPopulationRemotePath, String stateAcronymRemotePath) {
		Map<String, Integer> mapTotalPopulation = new HashMap<String, Integer>();
		try {
			
			String line, cityName, state, acronym;
			Integer cityPopulation;
			
			BufferedReader populationReader = generateReaderFromConnection(server, username, password, citiesPopulationRemotePath);
			
			BufferedReader stateReader = generateReaderFromConnection(server, username, password, stateAcronymRemotePath);
			
			List<String> lstStateReader = stateReader.lines().collect(Collectors.toList());

			while ((line = populationReader.readLine()) != null) {

				if (!line.isEmpty()) {
					cityName = line.substring(0, line.indexOf("\t")).trim();
					state = line.substring(line.indexOf("\t"), indexOf(line, "\t", 2)).trim();
					cityPopulation = Integer.parseInt(line.substring(indexOf(line, "\t", 2)).trim());
					
					for (String sr : lstStateReader)
					{
						if (sr.contains(state))
						{
							acronym = sr.substring(sr.indexOf("\t")).trim();
							mapTotalPopulation.put(cityName + ", " + acronym, cityPopulation);
						}
					}
					
				}
			}
			if (!mapTotalPopulation.isEmpty())
				return mapTotalPopulation;
			
		}

		catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static void sendFileToFTP(File file, String server, Integer port, String username, String password)
	{
		FTPClient ftp = new FTPClient();
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
	            return;
	        }
			is.close();
			
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		finally
		{
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

	public static File getWebPageData(String url) throws Exception {
		
		System.setProperty("webdriver.chrome.driver", System.getProperty("user.dir") + System.getProperty("file.separator") + "chromedriver.exe");
		WebDriver driver = new ChromeDriver();
		driver.get(url);
		String todayDate = getTodayDate();

		File file = new File(System.getProperty("user.home"), todayDate + "RawInfectedToday.txt");
		BufferedWriter bf = new BufferedWriter(new FileWriter(file));

		bf.write(new String(driver.getPageSource().getBytes(UTF_8), ISO));
		bf.close();
		driver.quit();
		
		return file;
    }
	
	public static void main(String[] args) throws Exception {
		
		Properties prop = new Properties();
		prop.load(new FileInputStream(System.getProperty("user.home") + System.getProperty("file.separator") + "data.properties"));
		
		String server = prop.getProperty("server").trim();
		Integer port = Integer.parseInt(prop.getProperty("port"));
		String username = prop.getProperty("username").trim();
		String password = prop.getProperty("password").trim();
		
		
		String citiesPopulationRemotePath = "/My Documents/cidadesXPopulacao.txt"; 
		String stateAcronymRemotePath = "/My Documents/EstadosxSigla.txt";
		  
		System.out.println("Iniciando carga de arquivos");
		  
		File file = getWebPageData(url); 
		Map<String, Integer> infectedToday = listInfectedToday(file);
		  
		Map<String, Integer> totalPopulation =
		readTotalPopulationFromFTP(server, username, password, citiesPopulationRemotePath, stateAcronymRemotePath);
		
		if (!infectedToday.isEmpty() || !totalPopulation.isEmpty())
			System.out.println("Arquivos gerados com sucesso");
		
		System.out.println("Gerando analise dos dados...");
		File finalFile = generateAnalytics(infectedToday, totalPopulation); 
		sendFileToFTP(finalFile, server, port, username, password);
		 
	}
}
