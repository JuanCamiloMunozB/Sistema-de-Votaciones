import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData; // Necesario para pasar propiedades
import com.zeroc.Ice.Properties;         // Necesario para crear propiedades
import com.zeroc.Ice.Util;
import ElectionSystem.ServerServicePrx;
import ElectionSystem.ElectionData;

public class TestClient {
  public static void main(String[] args) {
    // Alternativa más robusta: Cargar la configuración desde un archivo
    InitializationData initData = new InitializationData();
    initData.properties = Util.createProperties();
    // Intenta cargar desde un archivo de propiedades específico.
    // El archivo config.client debe estar en el directorio de trabajo o especificar una ruta.
    try {
        initData.properties.load("config.client"); 
    } catch (Exception e) {
        System.err.println("Advertencia: No se pudo cargar config.client. " + e.getMessage());
        // Si no se carga, el locator deberá definirse de otra forma (ej. variable de entorno o -D)
        // o se usará el default de Ice si no se especifica locator y falla.
        // Para asegurar, podemos setearlo aquí si falla la carga y no queremos depender de -D
         if (System.getProperty("Ice.Default.Locator") == null) {
             System.out.println("Estableciendo Ice.Default.Locator por defecto en código ya que config.client no se cargó y -D no está presente.");
             initData.properties.setProperty("Ice.Default.Locator", "IceGrid/Locator:tcp -h localhost -p 4062");
         }
    }

    // Pasar args también permite que se sobreescriban propiedades desde la línea de comando si es necesario
    // y también carga el archivo de configuración por defecto si Ice.Config no está seteado.
    // String[] iceArgs = new String[] { "--Ice.Config=config.client" }; // Podrías forzarlo así
    // try (Communicator communicator = Util.initialize(iceArgs, initData)) { 
    try (Communicator communicator = Util.initialize(args, initData)) { // Usar args originales y initData con propiedades cargadas
      
      System.out.println("Usando Locator: " + communicator.getProperties().getProperty("Ice.Default.Locator"));
      System.out.println("Intentando obtener proxy para ServerService...");
      ServerServicePrx server = ServerServicePrx.checkedCast(
          communicator.stringToProxy("ServerService")); 

      if (server == null) {
        System.err.println("Proxy nulo para ServerService. ¿Está IceGrid Registry corriendo y la app desplegada?");
        return;
      }
      System.out.println("Proxy a ServerService obtenido.");

      // ... (resto de tu código de prueba)
      System.out.println("Llamando a getElectionData(0) en ServerService...");
      ElectionData electionData = server.getElectionData(0); 

      if (electionData != null) {
        System.out.println("Respuesta de getElectionData:");
        System.out.println("  Nombre Elección: " + electionData.name);
        // ... resto de los prints ...
      } else {
        System.err.println("getElectionData devolvió null.");
      }
      System.out.println("Llamada a ServerService completada.");

    } catch (com.zeroc.Ice.LocalException e) {
      System.err.println("Error de Ice localizando o comunicando con ServerService: " + e);
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println("Error inesperado en TestClient: " + e);
      e.printStackTrace();
    }
  }
}