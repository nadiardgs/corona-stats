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

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;

public class Main {

	private final static String url = "https://especiais.g1.globo.com/bemestar/coronavirus/mapa-coronavirus/";

	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final Charset ISO = Charset.forName("ISO-8859-1");

	// write the raw web page's content into file and returns it as argument
	public static File getWebPageData(String myURL) throws IOException {
		JBrowserDriver driver = new JBrowserDriver(Settings.builder().build());
		driver.get(myURL);

		String todayDate = getTodayDate();

		File file = new File(System.getProperty("user.home"), todayDate + "RawInfectedToday.txt");
		BufferedWriter bf = new BufferedWriter(new FileWriter(file));

		bf.write(new String(driver.getPageSource().getBytes(UTF_8), ISO));
		bf.close();
		driver.quit();

		return file;
	}

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

		try {
			
			OutputStreamWriter bf = new OutputStreamWriter(new FileOutputStream(
					file),
					StandardCharsets.ISO_8859_1);

			Double percentage;

			List<String> lInfected = new ArrayList<String>(listInfected.keySet());
			Collections.sort(lInfected);
			List<String> lTotalPopulation = new ArrayList<String>(totalPopulation.keySet());

			for (String l : lInfected) {
				for (String tp : lTotalPopulation) {
					if (l.equalsIgnoreCase(tp)) {
						percentage = ((double) listInfected.get(tp) * 100 / (double) totalPopulation.get(tp));

						Double toBeTruncated = new Double(percentage);

						Double truncatedDouble = BigDecimal.valueOf(toBeTruncated).setScale(3, RoundingMode.HALF_UP)
								.doubleValue();

						bf.write(l + ": " + truncatedDouble + " por cento da populacao infectada.");
						bf.write("\n");
					}
				}
			}
			bf.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
						cityName = list.get(i).substring(list.get(i).indexOf(">") + 1, list.get(i).indexOf(","));
						cityInfected = Integer.parseInt(cityInfectedString);
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

	public static Map<String, Integer> readTotalPopulationFromFTP(String remotePath, String server, String username, String password) {
		URL url;
		Map<String, Integer> mapTotalPopulation = new HashMap<String, Integer>();
		try {
			url = new URL(remotePath.trim());
			URLConnection con = url.openConnection();
			BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String line, cityName;
			Integer cityPopulation;

			while ((line = br.readLine()) != null) {

				if (!line.isEmpty()) {
					cityPopulation = Integer.parseInt(line.substring(line.indexOf("\t")).trim());
					cityName = line.substring(0, line.indexOf("\t"));
					mapTotalPopulation.put(cityName, cityPopulation);
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

	public static void sendFileToFTP(File file, String server, String port, String username, String password)
	{
		FTPClient ftp = new FTPClient();
		try {
			;
			ftp.connect(server.trim(), Integer.parseInt(port.trim()));
			ftp.login(username, password);
			ftp.enterLocalPassiveMode();
			
			ftp.setFileType(FTP.BINARY_FILE_TYPE);
			InputStream is = new FileInputStream(file);
			
			boolean success = ftp.storeFile(file.getName(), is);
			is.close();
			if (success)
			{
				System.out.println("Upload de arquivo realizado com sucesso");
			}
			
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public static void main(String[] args) throws IOException {
		
		Properties prop = new Properties();
		prop.load(new FileInputStream(System.getProperty("user.home") + System.getProperty("file.separator") + "data.properties"));
		
		String server = prop.getProperty("server");
		String port = prop.getProperty("port");
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		
		String remotePath = "ftp://" + username + ":" + password + "@" + server + "/My Documents/cidadesXPopulacao.txt";
		
		File file = getWebPageData(url);
		Map<String, Integer> infectedToday = listInfectedToday(file);
		Map<String, Integer> totalPopulation = readTotalPopulationFromFTP(remotePath, server, username, password);
		
		File finalFile = generateAnalytics(infectedToday, totalPopulation);
		sendFileToFTP(finalFile, server, port, username, password);
	}
}
