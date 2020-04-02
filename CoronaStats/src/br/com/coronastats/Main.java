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
			List<String> lTotalPopulation = new ArrayList<String>(totalPopulation.keySet());

			
			Integer totalInfected = listInfected.values().stream().mapToInt(Integer::intValue).sum();
			
			Collections.sort(lInfected);
			
			Integer totalPop = totalPopulation.values().stream().mapToInt(Integer::intValue).sum();
			
			System.out.println("Total de infectados: " + totalInfected + "; Total da populacao: " + totalPop);
			
			Double finalPercentage = (double) totalInfected * 100 / (double) totalPop;
			finalPercentage = BigDecimal.valueOf(finalPercentage).setScale(3, RoundingMode.HALF_UP).doubleValue();
			
			bf.write("Brasil: " + totalInfected + " de " + totalPop + " habitantes infectados. Percentagem de contagio: "
					+ finalPercentage + "\n");
			
			for (String l : lInfected) {
				for (String tp : lTotalPopulation) {
					
					if (l.equalsIgnoreCase(tp)) {
						percentage = ((double) listInfected.get(tp) * 100 / (double) totalPopulation.get(tp));

						Double truncatedDouble = BigDecimal.valueOf(percentage).setScale(3, RoundingMode.HALF_UP)
								.doubleValue();
						
						bf.write(tp + ": " + listInfected.get(tp) + " de " + totalPopulation.get(tp) + 
								" habitantes infectados. Percentagem de contagio: " + truncatedDouble);
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

					cityInfectedString = list.get(i).substring(indexOf(list.get(i), ">", 3) + 1, indexOf(list.get(i), "<", 4)).replace(".", "");
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
	
	//this method was adapted from the generateReaderFromConnection to return a List<String> instead of a BufferedReader
	//to make the code cleaner and less likely to errors. Besides, I was turning the lines from the BufferedReader into Lists anyway
	public static List<String> generateListFromConnection(String remotePath) throws IOException
	{
		BufferedReader br = null;
		List<String> lst = new ArrayList<String>();
		URL url = null;
		URLConnection con = null;

		try {
			url = new URL(remotePath);
			con = url.openConnection();
			br = new BufferedReader(new InputStreamReader(con.getInputStream()));
		    
		    lst = br.lines().collect(Collectors.toList());
		    lst.removeAll(Arrays.asList("", null));
		    lst.forEach(System.out::println);
		    System.out.println("Arquivo " + remotePath + " recuperado com sucesso");
		    return lst;
		}

		catch (IOException e) {
			System.out.println("Erro ao recuperar o arquivo " + remotePath + ": " + e.getMessage());
		}
		
		finally
		{
			br.close();
		}
		
		return lst;
	}
	
	//this method returns a map with the name of the city + state acronym as key, and the population as value
	public static Map<String, Integer> readTotalPopulationFromFTP(String citiesPopulationRemotePath, String stateAcronymRemotePath) {
		Map<String, Integer> mapTotalPopulation = new HashMap<String, Integer>();
		try {
			
			String cityName, acronym, state;
			Integer cityPopulation;
			
			/* formatted like: city "\t" state "\t" population
			 * for example: Campinas "\t" Sao Paulo "\t" 4459347 */
			List<String> lstCitiesPopulation = generateListFromConnection(citiesPopulationRemotePath);
			lstCitiesPopulation.removeAll(Arrays.asList("", null));
			
			/* formatted like: state "\t" acronym
			 * for example: Sao Paulo "\t" SP */
			List<String> lstStateAcronym = generateListFromConnection(stateAcronymRemotePath);
			lstStateAcronym.removeAll(Arrays.asList("", null));
			
			for (String line : lstCitiesPopulation)
			{
				if (!line.isEmpty())
				{
					cityName = line.substring(0, line.indexOf("\t")).trim();
					state = line.substring(line.indexOf("\t"), indexOf(line, "\t", 2)).trim();
					cityPopulation = Integer.parseInt(line.substring(indexOf(line, "\t", 2)).trim());
					
					//return the line from the lstStateAcronym that contains the state of the lstCitiesPopulation (to test yet)
					/*List<String> lstGetStateAcronym = lstStateAcronym
			                .stream()
			                .filter(x -> x.contains(line.substring(line.indexOf("\t"), indexOf(line, "\t", 2)).trim())) //state
			                .collect(Collectors.toList());*/

					for (String stateItem : lstStateAcronym)
					{
						if (stateItem.contains(state))
						{
							acronym = stateItem.substring(stateItem.indexOf("\t")).trim();
							mapTotalPopulation.put(cityName + ", " + acronym, cityPopulation);
							System.out.println(cityName + ", " + acronym + ": " + cityPopulation);
						}
					}
				}
				
				/*if (!lstGetStateAcronym.isEmpty()) {
					String stringmatchingStateAcronym = lstGetStateAcronym.get(0);
				
					System.out.println(stringmatchingStateAcronym);
				
					acronym = stringmatchingStateAcronym.substring(stringmatchingStateAcronym.indexOf("\t")).trim();
					mapTotalPopulation.put(cityName + ", " + acronym, cityPopulation);
					System.out.println(cityName + ", " + acronym + ": " + cityPopulation);
				}*/
			}
			

			/*while ((line = populationReader.readLine()) != null) {

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
			}*/
		}

		catch (Exception e) {
			e.printStackTrace();
		}
		
		return mapTotalPopulation;
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
 
        //String citiesPopulationRemotePath = "https://gofile.io/?c=Sy9oA7";
		//String stateAcronymRemotePath = 	"https://gofile.io/?c=WqDOpk";
		//"https://filebin.net/zxvfuaht8lmiqrtc/cidadesXPopulacao.txt?t=0ezd4ti7", "https://filebin.net/zxvfuaht8lmiqrtc/EstadosxSigla.txt?t=w7m1lp1v"
		  
		String citiesPopulationRemotePath = "ftp://" + username + ":" + password + "@" + server + "/MyDocuments/cidadesXPopulacao.txt";
		String stateAcronymRemotePath 	  = "ftp://" + username + "@" + server + "/MyDocuments/EstadosxSigla.txt";
		
		System.out.println("Iniciando carga de arquivos");
		  
		File file = getWebPageData(url); 
		Map<String, Integer> infectedToday = listInfectedToday(file);
		  
		Map<String, Integer> totalPopulation =
		readTotalPopulationFromFTP("https://filebin.net/zxvfuaht8lmiqrtc/cidadesXPopulacao.txt?t=0r7f55jr", "https://filebin.net/zxvfuaht8lmiqrtc/EstadosxSigla.txt?t=zonmq72o");
		System.out.println("Populacao:");
		totalPopulation.entrySet().forEach(System.out::println);
		
		System.out.println("Gerando analise dos dados...");
		File finalFile = generateAnalytics(infectedToday, totalPopulation); 
		sendFileToFTP(finalFile, server, port, username, password);
		 
	}
}
