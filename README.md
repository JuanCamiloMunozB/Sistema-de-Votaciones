# Sistema de Votaciones

Sigue los pasos para compilar, construir y ejecutar el sistema:

## Paso 1: Compilar el proyecto

```bash
./gradlew clean build
```

## Paso 2: Construir los archivos JAR

```bash
./gradlew buildAllJars
```

## Paso 3: Ejecutar el registro de IceGrid

```bash
icegridregistry --Ice.Config=ice-grid/config.registry
```

## Paso 4: Ejecutar el nodo de IceGrid (en otra terminal)

```bash
icegridnode --Ice.Config=ice-grid/config.icegrid
```

## Paso 5: Agregar electionApp.xml al registro de IceGrid

Abre otra terminal (ahora tendrás tres terminales abiertas):

```bash
cd ice-grid
icegridadmin --Ice.Config=config.icegrid -e "application add electionApp.xml"
```

Si obtienes el error “application already exists”, actualiza la aplicación con:

```bash
icegridadmin --Ice.Config=config.icegrid -e "application update electionApp.xml"
```

Luego devuelve a la carpeta raíz del proyecto:

```bash
cd ..
```

## Ejecución según el requerimiento:

### Para emitir un voto

**Paso 6: Ejecutar los servidores de VotingTable**

```bash
java -DVOTING_TABLE_ID=3 -cp "voting_table/build/classes/java/main;jar-files/ice-3.7.9.jar;." VotingTableMain
```

### Para consultar la estación de votación

**Paso 6: Ejecutar el ProxyCache**

```bash
java -jar proxy_cache_server/build/libs/proxy_cache_server.jar
```

**Paso 7: Ejecutar el QueryStation (en otra terminal, tendrás cuatro terminales abiertas)**

```bash
java -jar query_station/build/libs/query_station.jar
```