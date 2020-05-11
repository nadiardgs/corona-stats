Hello, there!

You are currently at the corona-stats' GitHub repository.
corona-stats reads today's data on coronavirus spread in Brazilian cities (available at https://especiais.g1.globo.com/bemestar/coronavirus/mapa-coronavirus/), and uploads a file to a FTP remote server containing the populations' contamination percentage.

You are free to download and modify this project.

Keep in mind the following:

1) The project needs permission to write on your user's folder;
2) You'll need a FTP remote server to upload to. I configured mine at https://www.drivehq.com/secure/Account.aspx. If offers you 5GB free storage;
3) Download both the "cidadesXPopulacao.txt" and "EstadosxSigla.txt" files and upload them to the FTP Server. 
cidadesXPopulacao.txt has the cities' population data, which you'll need to calculate the percentage. EstadosXSigla.txt has the list of brazilian states with their specific acronyms. Obs: Be aware of the remotePath variable and change it to represent your actual remote path;
4) You'll have to configure a properties file named "data.properties" at your user's folder to access your FTP server. Example:



server=ftp.drivehq.com<br>
port=21<br>
username=your_user<br>
password=your_password
