package avilaos.pruebas;

import avilaos.config.ArchivoSistemaArchivos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.enums.Permiso;
import avilaos.enums.TipoEntrada;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.estructuras.NodoArbol;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.ResultadoFS;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;
import avilaos.so.disco.AsignadorBloques;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Pruebas de terminal de la etapa 13: sistema de archivos (árbol, CRUD, permisos y JSON). */
public final class PruebasPaso13 {

    private static final String DISCO = "SD-PC0";
    private static final String FS = "FS-PC0";
    private static final Usuario ADMIN = Usuario.ADMIN;
    private static final Usuario USER1 = Usuario.comun("user1");
    private static final Usuario USER2 = Usuario.comun("user2");

    /** JSON del sistema de archivos del enunciado, tal cual. */
    static final String EJEMPLO = """
            {
              "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/usuarios", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 1, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 1, "owner": "user1", "permissions": "private" },
                { "path": "/sistema/script.py", "type": "FILE", "startBlock": 62, "blocks": 1, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 1, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 1, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/img1.png", "type": "FILE", "startBlock": 119, "blocks": 1, "owner": "user1", "permissions": "private" },
                { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 1, "owner": "user2", "permissions": "public" },
                { "path": "/usuarios/video.mp4", "type": "FILE", "startBlock": 180, "blocks": 1, "owner": "user1", "permissions": "public" }
              ]
            }
            """;

    private static Resultados r;

    private PruebasPaso13() {
    }

    /** Ejecuta todas las pruebas de la etapa 13 e imprime su resumen. */
    public static void ejecutar(LogEventos log) {
        r = new Resultados(13, "Etapa 13: sistema de archivos (árbol, CRUD, permisos y JSON)");
        probarArbol();
        probarRutas();
        probarCrudAdministrador();
        probarRechazos();
        probarCargaDelEnunciado();
        probarPermisos();
        probarCargaTolerante();
        probarArchivosIncargables();
        probarGuardarYRecargar();
        probarConcurrencia();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El árbol propio: hijos, padres, profundidad y mover un nodo de padre. */
    private static void probarArbol() {
        r.grupo("Árbol propio (NodoArbol)");
        NodoArbol<String> raiz = new NodoArbol<>("/");
        NodoArbol<String> a = new NodoArbol<>("a");
        NodoArbol<String> b = new NodoArbol<>("b");
        NodoArbol<String> c = new NodoArbol<>("c");
        raiz.agregarHijo(a);
        raiz.agregarHijo(b);
        a.agregarHijo(c);
        r.verificar("la raíz tiene 2 hijos y el subárbol 4 nodos", raiz.getHijos().tamano() == 2 && raiz.tamanoSubarbol() == 4, "");
        r.verificar("profundidades: raíz 0, a 1, c 2", raiz.profundidad() == 0 && a.profundidad() == 1 && c.profundidad() == 2, "");
        r.verificar("padre de c es a", c.getPadre() == a, "");
        b.agregarHijo(c);
        r.verificar("mover c a b: a queda hoja y b lo tiene", a.esHoja() && b.getHijos().contiene(c) && c.getPadre() == b, "");
        r.verificar("quitar un hijo que no es suyo -> false", !a.quitarHijo(c), "");
        r.verificar("quitar c de b", b.quitarHijo(c) && c.getPadre() == null && raiz.tamanoSubarbol() == 3, "");
    }

    /** Rutas absolutas y nombres válidos. */
    private static void probarRutas() {
        r.grupo("Rutas y nombres");
        r.verificar("'/a/b/' se normaliza a '/a/b'", "/a/b".equals(SistemaArchivos.normalizar("/a/b/")), "");
        r.verificar("'/' es la raíz", "/".equals(SistemaArchivos.normalizar("/")), "");
        r.verificar("relativas, vacías, '//' y '..' no son válidas", SistemaArchivos.normalizar("a/b") == null
                && SistemaArchivos.normalizar("") == null && SistemaArchivos.normalizar("/a//b") == null
                && SistemaArchivos.normalizar("/a/../b") == null && SistemaArchivos.normalizar(null) == null, "");
        r.verificar("padre y nombre de /usuarios/notas.txt", "/usuarios".equals(SistemaArchivos.padreDe("/usuarios/notas.txt"))
                && "notas.txt".equals(SistemaArchivos.nombreDe("/usuarios/notas.txt")) && "/".equals(SistemaArchivos.padreDe("/sistema")), "");
        r.verificar("nombres inválidos: vacío, con barra, con espacios al borde, demasiado largo",
                SistemaArchivos.motivoNombre("") != null && SistemaArchivos.motivoNombre("a/b") != null
                && SistemaArchivos.motivoNombre(" a") != null && SistemaArchivos.motivoNombre("x".repeat(41)) != null, "");
        r.verificar("nombres válidos: con puntos, espacios internos y tildes", SistemaArchivos.motivoNombre("mi archivo.tar.gz") == null
                && SistemaArchivos.motivoNombre("canción.mp3") == null, "");
    }

    /** El administrador crea, lee, renombra, escribe y elimina; los bloques siguen al árbol. */
    private static void probarCrudAdministrador() {
        r.grupo("CRUD del administrador y bloques en el SD");
        Sistema s = nuevo(100, 20);
        r.verificar("crea /docs", s.fs.crearDirectorio(ADMIN, "/", "docs", "admin", Permiso.PUBLICO).exito(), "");
        ResultadoFS creado = s.fs.crearArchivo(ADMIN, "/docs", "tesis.pdf", 5, null, Permiso.PRIVADO, "P3-H1");
        InfoEntrada tesis = creado.entrada();
        r.verificar("crea /docs/tesis.pdf de 5 bloques desde el 0", creado.exito() && tesis.primerBloque() == 0 && tesis.bloques() == 5, "");
        r.verificar("la ficha tiene nombre, dueño, permiso, creador y tipo", "tesis.pdf".equals(tesis.nombre()) && "admin".equals(tesis.dueno())
                && tesis.permiso() == Permiso.PRIVADO && "P3-H1".equals(tesis.creador()) && tesis.tipo() == TipoEntrada.ARCHIVO, "");
        r.verificar("sus bloques en el SD forman la cadena 0..4 con su marca", "0,1,2,3,4".equals(unir(s.fs.bloquesDe("/docs/tesis.pdf")))
                && SistemaArchivos.marca(tesis.id()).equals(s.disco.leer(3).dueno()), unir(s.fs.bloquesDe("/docs/tesis.pdf")));
        r.verificar("el bloque sabe a qué archivo pertenece", "/docs/tesis.pdf".equals(s.fs.rutaDeBloque(s.disco.leer(2).dueno())), "");
        r.verificar("leer deja la última operación", s.fs.leer(ADMIN, "/docs/tesis.pdf").exito()
                && "Leer".equals(s.fs.buscar("/docs/tesis.pdf").ultimaOperacion()), "");
        r.verificar("actualizar sin nombre nuevo: escritura, misma estructura", s.fs.escribir(ADMIN, "/docs/tesis.pdf").exito()
                && s.fs.buscar("/docs/tesis.pdf").bloques() == 5, "");
        r.verificar("renombrar el directorio cambia la ruta de su contenido", s.fs.renombrar(ADMIN, "/docs", "documentos").exito()
                && s.fs.existe("/documentos/tesis.pdf") && !s.fs.existe("/docs"), "");
        r.verificar("los bloques no cambian al renombrar", "0,1,2,3,4".equals(unir(s.fs.bloquesDe("/documentos/tesis.pdf"))), "");
        s.fs.crearDirectorio(ADMIN, "/documentos", "viejos", "admin", Permiso.PUBLICO);
        s.fs.crearArchivo(ADMIN, "/documentos/viejos", "a.txt", 3, null, Permiso.PUBLICO, "P4-H1");
        s.fs.crearArchivo(ADMIN, "/documentos/viejos", "b.txt", 2, null, Permiso.PUBLICO, "P5-H1");
        ResultadoFS borrarArchivo = s.fs.eliminar(ADMIN, "/documentos/viejos/a.txt");
        r.verificar("eliminar un archivo libera sus 3 bloques", borrarArchivo.exito() && borrarArchivo.bloques() == 3
                && s.disco.contarLibres(false) == 100 - 7, s.disco.contarLibres(false) + "");
        ResultadoFS borrarTodo = s.fs.eliminar(ADMIN, "/documentos");
        r.verificar("eliminar un directorio borra todo su contenido y libera 7 bloques", borrarTodo.exito() && borrarTodo.bloques() == 7
                && !s.fs.existe("/documentos/viejos/b.txt") && s.disco.contarLibres(false) == 100, "");
        r.verificar("solo queda la raíz", s.fs.listarTodo().tamano() == 1, "");
        r.verificar("sistema de archivos íntegro", s.fs.verificarIntegridad() && s.disco.verificarIntegridad(), "");
    }

    /** Lo que no tiene sentido se rechaza con un motivo, sin tocar nada y sin ERROR. */
    private static void probarRechazos() {
        r.grupo("Rechazos con motivo (sin ERROR y sin cambios)");
        Sistema s = nuevo(20, 0);
        s.fs.crearDirectorio(ADMIN, "/", "d", "admin", Permiso.PUBLICO);
        s.fs.crearArchivo(ADMIN, "/d", "f", 15, null, Permiso.PUBLICO, "P1-H1");
        long errores = Debug.getErrores();
        r.verificar("crear en un directorio que no existe", motivo(s.fs.crearArchivo(ADMIN, "/nada", "x", 1, null, Permiso.PUBLICO, "P")).contains("no existe"), "");
        r.verificar("crear dentro de un archivo", motivo(s.fs.crearArchivo(ADMIN, "/d/f", "x", 1, null, Permiso.PUBLICO, "P")).contains("es un archivo"), "");
        r.verificar("nombre repetido", motivo(s.fs.crearArchivo(ADMIN, "/d", "f", 1, null, Permiso.PUBLICO, "P")).contains("ya existe"), "");
        String sinEspacio = motivo(s.fs.crearArchivo(ADMIN, "/d", "grande", 6, null, Permiso.PUBLICO, "P"));
        r.verificar("sin bloques suficientes (pide 6, quedan 5)", sinEspacio.contains("no hay bloques libres suficientes"), sinEspacio);
        r.verificar("y no queda nada a medias", !s.fs.existe("/d/grande") && s.disco.contarLibres(false) == 5, "");
        r.verificar("tamaño 0", motivo(s.fs.crearArchivo(ADMIN, "/d", "cero", 0, null, Permiso.PUBLICO, "P")).contains("al menos 1"), "");
        r.verificar("nombre inválido", !s.fs.crearDirectorio(ADMIN, "/", "a/b", "admin", Permiso.PUBLICO).exito(), "");
        r.verificar("renombrar o eliminar la raíz", motivo(s.fs.renombrar(ADMIN, "/", "x")).contains("raíz")
                && motivo(s.fs.eliminar(ADMIN, "/")).contains("raíz"), "");
        r.verificar("renombrar a un nombre que ya existe", s.fs.crearDirectorio(ADMIN, "/", "e", "admin", Permiso.PUBLICO).exito()
                && motivo(s.fs.renombrar(ADMIN, "/e", "d")).contains("ya existe"), "");
        r.verificar("escribir un directorio", motivo(s.fs.escribir(ADMIN, "/d")).contains("directorio"), "");
        r.verificar("operar sobre algo que no existe", motivo(s.fs.leer(ADMIN, "/d/nada")).contains("no existe"), "");
        r.verificar("ruta mal escrita", motivo(s.fs.eliminar(ADMIN, "d/f")).contains("no es válida"), "");
        r.verificar("ningún rechazo es ERROR (son situaciones normales)", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.verificar("sin usuario sí es ERROR", conError(() -> !s.fs.leer(null, "/d/f").exito()), "");
        r.verificar("sistema de archivos íntegro", s.fs.verificarIntegridad(), "");
    }

    /** El JSON del enunciado se carga completo y cada archivo queda en su bloque. */
    private static void probarCargaDelEnunciado() {
        r.grupo("Carga del JSON del enunciado");
        DatosSistemaArchivos datos = ArchivoSistemaArchivos.interpretar(EJEMPLO);
        r.verificar("se puede cargar, sin observaciones", datos.cargable() && datos.problemas().estaVacia(), primeros(datos.problemas()));
        r.verificar("disco de 200 bloques y cabezal en 50", datos.totalBloques() == 200 && datos.cabezalInicial() == 50, "");
        r.verificar("10 entradas válidas", datos.entradas().tamano() == 10, datos.entradas().tamano() + "");
        Sistema s = cargarEjemplo();
        r.verificar("las 10 entradas quedan en el árbol (+ la raíz)", s.fs.listarTodo().tamano() == 11, s.fs.listarTodo().tamano() + "");
        boolean bloques = s.fs.buscar("/sistema/boot.bin").primerBloque() == 11 && s.fs.buscar("/usuarios/notas.txt").primerBloque() == 34
                && s.fs.buscar("/sistema/script.py").primerBloque() == 62 && s.fs.buscar("/sistema/core.dll").primerBloque() == 64
                && s.fs.buscar("/sistema/config.sys").primerBloque() == 95 && s.fs.buscar("/usuarios/img1.png").primerBloque() == 119
                && s.fs.buscar("/usuarios/data.csv").primerBloque() == 123 && s.fs.buscar("/usuarios/video.mp4").primerBloque() == 180;
        r.verificar("cada archivo en su startBlock: 11, 34, 62, 64, 95, 119, 123, 180", bloques, "");
        InfoEntrada notas = s.fs.buscar("/usuarios/notas.txt");
        r.verificar("notas.txt: de user1, privado, creado por la carga inicial", "user1".equals(notas.dueno()) && notas.permiso() == Permiso.PRIVADO
                && SistemaArchivos.CREADOR_CARGA.equals(notas.creador()), "");
        r.verificar("8 bloques ocupados en el disco", s.disco.contarLibres(false) == 192, "");
        r.verificar("dueños conocidos: admin, user1, user2", s.fs.duenos().tamano() == 3 && contiene(s.fs.duenos(), "user2"),
                primeros(s.fs.duenos()));
        r.verificar("archivos para la tabla de asignación: 8", s.fs.listarArchivos().tamano() == 8, "");
        r.verificar("sistema íntegro", s.fs.verificarIntegridad() && s.disco.verificarIntegridad(), "");
        r.seccion(" JSON del enunciado: 2 directorios y 8 archivos en los bloques 11, 34, 62, 64, 95, 119, 123 y 180");
    }

    /** Modo usuario: solo lo propio o lo público, y cada rechazo explica por qué. */
    private static void probarPermisos() {
        r.grupo("Permisos: modo administrador y modo usuario");
        Sistema s = cargarEjemplo();
        r.verificar("user1 lee su archivo privado notas.txt", s.fs.motivoLeer(USER1, "/usuarios/notas.txt") == null, "");
        String privado = s.fs.motivoLeer(USER2, "/usuarios/notas.txt");
        r.verificar("user2 NO lee notas.txt: 'es privado de user1'", privado != null && privado.contains("privado de user1"), privado);
        r.verificar("user1 lee data.csv (público de user2)", s.fs.motivoLeer(USER1, "/usuarios/data.csv") == null, "");
        r.verificar("cualquiera ve los directorios (la estructura)", s.fs.motivoLeer(USER2, "/sistema") == null, "");
        r.verificar("user1 NO crea directorios", s.fs.motivoCrear(USER1, "/usuarios", TipoEntrada.DIRECTORIO).contains("solo el administrador"), "");
        String ajeno = s.fs.motivoCrear(USER1, "/usuarios", TipoEntrada.ARCHIVO);
        r.verificar("user1 NO crea archivos en /usuarios (es del admin)", ajeno != null && ajeno.contains("sus propios directorios"), ajeno);
        r.verificar("el admin crea /usuarios/user1 para user1", s.fs.crearDirectorio(ADMIN, "/usuarios", "user1", "user1", Permiso.PRIVADO).exito(), "");
        ResultadoFS propio = s.fs.crearArchivo(USER1, "/usuarios/user1", "diario.txt", 2, null, Permiso.PRIVADO, "P8-H1");
        r.verificar("user1 sí crea archivos en su directorio, a su nombre", propio.exito() && "user1".equals(propio.entrada().dueno()), "");
        r.verificar("user1 NO crea a nombre de otro", motivo(s.fs.crearArchivo(USER1, "/usuarios/user1", "x", 1, "user2", Permiso.PUBLICO, "P")).contains("a su nombre"), "");
        r.verificar("user1 NO renombra (solo el administrador)", motivo(s.fs.renombrar(USER1, "/usuarios/notas.txt", "n.txt")).contains("solo el administrador"), "");
        r.verificar("user1 modifica su notas.txt", s.fs.escribir(USER1, "/usuarios/notas.txt").exito(), "");
        r.verificar("user1 NO modifica data.csv (de user2)", motivo(s.fs.escribir(USER1, "/usuarios/data.csv")).contains("sus propios archivos"), "");
        r.verificar("user1 NO modifica archivos del sistema", s.fs.motivoEscribir(USER1, "/sistema/boot.bin") != null
                && s.fs.motivoEliminar(USER1, "/sistema/boot.bin") != null, "");
        r.verificar("user2 NO elimina notas.txt (de user1)", motivo(s.fs.eliminar(USER2, "/usuarios/notas.txt")).contains("sus propios archivos"), "");
        r.verificar("user1 NO elimina directorios", s.fs.motivoEliminar(USER1, "/usuarios/user1").contains("solo el administrador"), "");
        r.verificar("user1 elimina su img1.png", s.fs.eliminar(USER1, "/usuarios/img1.png").exito() && s.disco.leer(119).dueno() == null, "");
        r.verificar("el administrador puede todo (renombra un archivo de user2)", s.fs.renombrar(ADMIN, "/usuarios/data.csv", "datos.csv").exito(), "");
        r.verificar("sistema íntegro", s.fs.verificarIntegridad(), "");
    }

    /** Un JSON con entradas malas: se apartan y se reportan, el resto se carga. */
    private static void probarCargaTolerante() {
        r.grupo("Carga tolerante: aparta lo malo y carga el resto");
        String malo = """
                {
                  "diskConfig": { "totalBlocks": 100, "initialHead": 10, "rpm": 7200 },
                  "fileSystem": [
                    { "path": "/fotos/a.jpg", "type": "FILE", "startBlock": 5, "blocks": 2, "owner": "user1", "permissions": "public" },
                    { "path": "/fotos", "type": "DIRECTORY", "owner": "user1", "permissions": "public" },
                    { "path": "/raro", "type": "FOLDER", "owner": "admin", "permissions": "public" },
                    { "path": "/sin-dueno", "type": "DIRECTORY", "permissions": "public" },
                    { "path": "/secreto", "type": "DIRECTORY", "owner": "admin", "permissions": "secret" },
                    { "path": "/fotos/cero.jpg", "type": "FILE", "startBlock": 9, "blocks": 0, "owner": "user1", "permissions": "public" },
                    { "path": "/fotos/lejos.jpg", "type": "FILE", "startBlock": 999, "blocks": 1, "owner": "user1", "permissions": "public" },
                    { "path": "relativa.txt", "type": "FILE", "startBlock": 1, "blocks": 1, "owner": "admin", "permissions": "public" },
                    { "path": "/huerfano/x.txt", "type": "FILE", "startBlock": 20, "blocks": 1, "owner": "admin", "permissions": "public" },
                    { "path": "/fotos/a.jpg", "type": "FILE", "startBlock": 30, "blocks": 1, "owner": "user1", "permissions": "public" },
                    { "path": "/fotos/choca.jpg", "type": "FILE", "startBlock": 6, "blocks": 1, "owner": "user1", "permissions": "public" },
                    { "path": "/fotos/auto.jpg", "type": "FILE", "blocks": 3, "owner": "user1", "permissions": "private" },
                    { "path": "/docs", "type": "DIRECTORY", "owner": "admin", "permissions": "public", "blocks": 4, "color": "rojo" },
                    "no soy un objeto",
                    { "path": "/fotos/bien.jpg", "type": "FILE", "startBlock": 40, "blocks": 1.0, "owner": "user1", "permissions": "PUBLIC" }
                  ]
                }
                """;
        long errores = Debug.getErrores();
        DatosSistemaArchivos datos = ArchivoSistemaArchivos.interpretar(malo);
        r.verificar("el archivo se puede cargar aunque tenga entradas malas", datos.cargable(), "");
        r.verificar("se apartan 7 entradas al leer (tipo, dueño, permiso, bloques 0, fuera del disco, ruta relativa, no objeto)",
                datos.entradas().tamano() == 8, datos.entradas().tamano() + " válidas");
        Sistema s = nuevo(datos.totalBloques(), 0);
        ListaEnlazada<String> problemas = datos.problemas();
        int cargadas = s.fs.cargar(datos.entradas(), problemas);
        r.verificar("al aplicar se apartan 3 más (padre inexistente, ruta repetida, bloque ocupado): quedan 5", cargadas == 5, cargadas + "");
        r.verificar("el archivo listado antes que su directorio igual se carga (directorios primero)", s.fs.existe("/fotos/a.jpg")
                && "5,6".equals(unir(s.fs.bloquesDe("/fotos/a.jpg"))), "");
        r.verificar("sin startBlock: primeros libres (0, 1, 2) y queda anotado", "0,1,2".equals(unir(s.fs.bloquesDe("/fotos/auto.jpg")))
                && contiene(problemas, "no indica 'startBlock'"), unir(s.fs.bloquesDe("/fotos/auto.jpg")));
        r.verificar("'1.0' bloques y 'PUBLIC' se aceptan", s.fs.existe("/fotos/bien.jpg"), "");
        r.verificar("directorio con bloques: se carga y se avisa", s.fs.existe("/docs") && contiene(problemas, "no ocupa bloques"), "");
        r.verificar("claves desconocidas (rpm, color): avisadas, no apartan", contiene(problemas, "'rpm'") && contiene(problemas, "'color'"), "");
        r.verificar("cada problema dice qué entrada y por qué", contiene(problemas, "entrada 3 (/raro)") && contiene(problemas, "DIRECTORY o FILE")
                && contiene(problemas, "entrada 9 (/huerfano/x.txt)") && contiene(problemas, "ya está ocupado")
                && contiene(problemas, "entrada 14: no es un objeto"), primeros(problemas));
        r.verificar("14 observaciones en total", problemas.tamano() == 14, problemas.tamano() + ": " + primeros(problemas));
        r.verificar("ninguna es ERROR: la simulación no se detiene", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.verificar("lo cargado está íntegro", s.fs.verificarIntegridad() && s.disco.verificarIntegridad(), "");
        r.seccion(" Carga tolerante: 15 entradas -> 5 cargadas, 14 observaciones reportadas");
    }

    /** Sin las secciones raíz el archivo no se puede usar; valores del disco fuera de rango se corrigen. */
    private static void probarArchivosIncargables() {
        r.grupo("Archivos que no se pueden cargar y disco corregido");
        r.verificar("texto que no es JSON", conError(() -> !ArchivoSistemaArchivos.interpretar("{ esto no").cargable()), "");
        r.verificar("raíz que no es objeto", conError(() -> !ArchivoSistemaArchivos.interpretar("[1, 2]").cargable()), "");
        r.verificar("sin diskConfig", conError(() -> !ArchivoSistemaArchivos.interpretar("{ \"fileSystem\": [] }").cargable()), "");
        r.verificar("sin fileSystem", conError(() -> !ArchivoSistemaArchivos.interpretar("{ \"diskConfig\": { \"totalBlocks\": 100, \"initialHead\": 0 } }").cargable()), "");
        r.verificar("archivo inexistente", conError(() -> !ArchivoSistemaArchivos.leer("no/existe/fs.json").cargable()), "");
        DatosSistemaArchivos raro = ArchivoSistemaArchivos.interpretar("{ \"diskConfig\": { \"totalBlocks\": 3, \"initialHead\": -4 }, \"fileSystem\": [] }");
        r.verificar("totalBlocks fuera de rango -> 200 e initialHead -> 0, anotados", raro.cargable() && raro.totalBloques() == 200
                && raro.cabezalInicial() == 0 && raro.problemas().tamano() == 2, primeros(raro.problemas()));
        DatosSistemaArchivos cabezal = ArchivoSistemaArchivos.interpretar("{ \"diskConfig\": { \"totalBlocks\": 120, \"initialHead\": 120 }, \"fileSystem\": [] }");
        r.verificar("initialHead igual al total (fuera del disco) -> 0", cabezal.cabezalInicial() == 0 && cabezal.totalBloques() == 120, "");
    }

    /** Lo guardado se vuelve a cargar idéntico (persistencia para futuras ejecuciones). */
    private static void probarGuardarYRecargar() {
        r.grupo("Guardar y recargar el sistema de archivos");
        Sistema s = cargarEjemplo();
        s.fs.crearDirectorio(ADMIN, "/usuarios", "user2", "user2", Permiso.PRIVADO);
        s.fs.crearArchivo(USER2, "/usuarios/user2", "tarea.docx", 4, null, Permiso.PRIVADO, "P12-H1");
        s.fs.eliminar(ADMIN, "/sistema/core.dll");
        s.fs.renombrar(ADMIN, "/usuarios/video.mp4", "pelicula.mp4");
        s.fs.crearArchivo(ADMIN, "/sistema", "x.tmp", 2, null, Permiso.PUBLICO, "P13-H1");
        s.fs.crearArchivo(ADMIN, "/sistema", "y.tmp", 1, null, Permiso.PUBLICO, "P14-H1");
        s.fs.eliminar(ADMIN, "/sistema/x.tmp");
        s.fs.crearArchivo(ADMIN, "/sistema", "huecos.log", 4, null, Permiso.PUBLICO, "P15-H1");
        s.fs.eliminar(ADMIN, "/sistema/y.tmp");
        String huecos = unir(s.fs.bloquesDe("/sistema/huecos.log"));
        String ruta;
        try {
            Path carpeta = Files.createTempDirectory("avilaos-fs");
            ruta = carpeta.resolve("sistema_archivos.json").toString();
        } catch (IOException e) {
            r.verificar("carpeta temporal", false, e.getMessage());
            return;
        }
        r.verificar("se guarda sin error", ArchivoSistemaArchivos.guardar(s.fs, s.disco, ruta), "");
        r.verificar("no queda el archivo temporal (escritura atómica)", !Files.exists(Path.of(ruta + ".tmp")), "");
        String texto = leerTexto(ruta);
        r.verificar("usa el formato del enunciado (diskConfig, fileSystem, path, type, startBlock, blocks, owner, permissions)",
                texto.contains("\"diskConfig\"") && texto.contains("\"totalBlocks\": 200") && texto.contains("\"fileSystem\"")
                && texto.contains("\"type\": \"FILE\"") && texto.contains("\"permissions\": \"private\""), "");
        DatosSistemaArchivos datos = ArchivoSistemaArchivos.leer(ruta);
        Sistema otro = nuevo(datos.totalBloques(), 0);
        int cargadas = otro.fs.cargar(datos.entradas(), datos.problemas());
        r.verificar("se recarga sin observaciones: 12 entradas", datos.problemas().estaVacia() && cargadas == 12, cargadas + " " + primeros(datos.problemas()));
        r.verificar("una cadena con huecos (" + huecos + ") vuelve idéntica gracias a 'chain'",
                huecos.equals(unir(otro.fs.bloquesDe("/sistema/huecos.log"))), unir(otro.fs.bloquesDe("/sistema/huecos.log")));
        r.verificar("mismo árbol con los mismos bloques, dueños, permisos y creadores", mismasEntradas(s.fs, otro.fs), "");
        r.verificar("se conservan el renombrado y el borrado", otro.fs.existe("/usuarios/pelicula.mp4") && !otro.fs.existe("/sistema/core.dll")
                && "P12-H1".equals(otro.fs.buscar("/usuarios/user2/tarea.docx").creador()), "");
        r.verificar("el disco recargado tiene la misma ocupación", otro.disco.contarLibres(false) == s.disco.contarLibres(false), "");
        r.verificar("y está íntegro", otro.fs.verificarIntegridad(), "");
    }

    /** Varios hilos operan a la vez sobre el sistema de archivos: el Semaphore lo mantiene íntegro. */
    private static void probarConcurrencia() {
        r.grupo("Concurrencia: 4 usuarios creando y borrando a la vez");
        Sistema s = nuevo(400, 0);
        for (int h = 0; h < 4; h++) {
            s.fs.crearDirectorio(ADMIN, "/", "u" + h, "u" + h, Permiso.PRIVADO);
        }
        long errores = Debug.getErrores();
        AtomicInteger excepciones = new AtomicInteger();
        Semaphore fin = new Semaphore(0);
        for (int h = 0; h < 4; h++) {
            final int hilo = h;
            new Thread(() -> {
                try {
                    Usuario yo = Usuario.comun("u" + hilo);
                    for (int i = 0; i < 150; i++) {
                        s.fs.crearArchivo(yo, "/u" + hilo, "f" + i, 1 + i % 4, null, Permiso.PUBLICO, "P" + hilo);
                        if (i % 3 == 2) {
                            s.fs.eliminar(yo, "/u" + hilo + "/f" + (i - 1));
                        }
                        s.fs.leer(yo, "/u" + ((hilo + 1) % 4) + "/f" + i);
                    }
                } catch (RuntimeException e) {
                    excepciones.incrementAndGet();
                } finally {
                    fin.release();
                }
            }, "Prueba-FS-" + h).start();
        }
        fin.acquireUninterruptibly(4);
        r.verificar("sin excepciones", excepciones.get() == 0, excepciones.get() + "");
        r.verificar("sistema de archivos y disco íntegros", s.fs.verificarIntegridad() && s.disco.verificarIntegridad(), "");
        r.verificar("sin ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Disco con su asignador y su sistema de archivos. */
    private record Sistema(DiscoSD disco, SistemaArchivos fs) {
    }

    /** Sistema vacío con los bloques y el swap indicados. */
    private static Sistema nuevo(int bloques, int swap) {
        DiscoSD disco = new DiscoSD(DISCO, bloques, swap, 0, 1);
        return new Sistema(disco, new SistemaArchivos(FS, new AsignadorBloques("Asignador-PC0", disco)));
    }

    /** Sistema cargado con el JSON del enunciado. */
    static Sistema cargarEjemplo() {
        DatosSistemaArchivos datos = ArchivoSistemaArchivos.interpretar(EJEMPLO);
        DiscoSD disco = new DiscoSD(DISCO, datos.totalBloques(), 100, datos.cabezalInicial(), 1);
        SistemaArchivos fs = new SistemaArchivos(FS, new AsignadorBloques("Asignador-PC0", disco));
        fs.cargar(datos.entradas(), datos.problemas());
        return new Sistema(disco, fs);
    }

    /** Indica si los dos sistemas tienen exactamente las mismas entradas, sin importar el orden de los hermanos. */
    private static boolean mismasEntradas(SistemaArchivos a, SistemaArchivos b) {
        ListaEnlazada<String> deA = firmas(a);
        ListaEnlazada<String> deB = firmas(b);
        if (deA.tamano() != deB.tamano()) {
            return false;
        }
        Nodo<String> nodo = deA.getCabeza();
        while (nodo != null) {
            if (!contiene(deB, nodo.getDato())) {
                return false;
            }
            nodo = nodo.getSiguiente();
        }
        return true;
    }

    /** Una línea por entrada: ruta, tipo, dueño, permiso, creador y bloques. */
    private static ListaEnlazada<String> firmas(SistemaArchivos fs) {
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        Nodo<InfoEntrada> nodo = fs.listarTodo().getCabeza();
        while (nodo != null) {
            InfoEntrada e = nodo.getDato();
            lista.agregarFinal(e.ruta() + "|" + e.tipo() + "|" + e.dueno() + "|" + e.permiso() + "|" + e.creador() + "|"
                    + unir(fs.bloquesDe(e.ruta())));
            nodo = nodo.getSiguiente();
        }
        return lista;
    }

    /** "0,1,2" a partir de un arreglo. */
    private static String unir(int[] valores) {
        StringBuilder sb = new StringBuilder();
        for (int v : valores) {
            sb.append(sb.length() > 0 ? "," : "").append(v);
        }
        return sb.toString();
    }

    /** Motivo del rechazo, o "" si la operación se hizo. */
    private static String motivo(ResultadoFS resultado) {
        return resultado.exito() ? "" : resultado.motivo();
    }

    /** Indica si algún problema contiene el texto. */
    private static boolean contiene(ListaEnlazada<String> problemas, String texto) {
        Nodo<String> nodo = problemas.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().contains(texto)) {
                return true;
            }
            nodo = nodo.getSiguiente();
        }
        return false;
    }

    /** Los problemas unidos, para el detalle de una prueba fallida. */
    private static String primeros(ListaEnlazada<String> problemas) {
        StringBuilder sb = new StringBuilder();
        Nodo<String> nodo = problemas.getCabeza();
        while (nodo != null) {
            sb.append("\n            - ").append(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Contenido de un archivo de texto, o "" si no se puede leer. */
    private static String leerTexto(String ruta) {
        try {
            return Files.readString(Path.of(ruta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Ejecuta la comprobación y exige que haya emitido al menos un ERROR. */
    private static boolean conError(Comprobacion c) {
        long antes = Debug.getErrores();
        boolean resultado = c.cumple();
        return resultado && Debug.getErrores() > antes;
    }

    /** Comprobación que se ejecuta midiendo los ERROR emitidos. */
    private interface Comprobacion {

        boolean cumple();
    }
}
