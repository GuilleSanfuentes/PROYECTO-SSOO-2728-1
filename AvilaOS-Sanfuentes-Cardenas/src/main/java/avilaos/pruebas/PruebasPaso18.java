package avilaos.pruebas;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.config.DatosProcesos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.enums.Permiso;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.metricas.MetricasDisco;
import avilaos.so.DatosProcesoArchivo;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;
import avilaos.vista.gui.AccionArchivo;
import avilaos.vista.gui.ColoresArchivo;
import avilaos.vista.gui.DialogoCarga;
import avilaos.vista.gui.DialogoOperacionArchivo;
import avilaos.vista.gui.FilaProceso;
import avilaos.vista.gui.FotoArchivos;
import avilaos.vista.gui.FotoCluster;
import avilaos.vista.gui.FotoComputador;
import avilaos.vista.gui.FotoDisco;
import avilaos.vista.gui.ModeloColaDisco;
import avilaos.vista.gui.ModeloTablaAsignacion;
import avilaos.vista.gui.OpcionesArchivo;
import avilaos.vista.gui.PanelMetricasDisco;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Pruebas de terminal de la etapa 18: lo que la interfaz del Proyecto 2 calcula y muestra (sin abrir ventanas). */
public final class PruebasPaso18 {

    private static final String RUTA_FS = DialogoCarga.FS_ENUNCIADO;
    private static final String RUTA_PROCESOS = DialogoCarga.PROCESOS_ENUNCIADO;
    private static final Usuario ADMIN = Usuario.ADMIN;
    private static final Usuario USER1 = Usuario.comun("user1");
    private static final Usuario USER2 = Usuario.comun("user2");
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso18() {
    }

    /** Ejecuta todas las pruebas de la etapa 18 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(18, "Etapa 18: interfaz del disco, el sistema de archivos y la suspensión");
        probarCarga();
        probarFotosDeColas();
        probarFotoDisco();
        probarFotoArchivos();
        probarColores();
        probarOpcionesDelMenu();
        probarFormulario();
        probarProcesoDesdeLaInterfaz();
        probarModelos();
        probarMetricasYPolitica();
        probarFotosConcurrentes();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** La carga exige los dos JSON; los textos de estado dicen qué falta; armar con procesos crea los 8 del enunciado. */
    private static void probarCarga() {
        r.grupo("Carga obligatoria de los dos JSON");
        long errores = Debug.getErrores();
        CargaSimulacion carga = new CargaSimulacion();
        r.verificar("sin JSON: los dos estados dicen que falta", DialogoCarga.textoSistema(null).contains("falta")
                && DialogoCarga.textoProcesos(null).contains("falta"), "");
        r.verificar("sin JSON: no se puede armar y el motivo nombra a los dos", !carga.armarConProcesos(log).armado()
                && carga.motivoNoArmar().contains("sistema de archivos") && carga.motivoNoArmar().contains("procesos"), carga.motivoNoArmar());
        DatosSistemaArchivos fs = carga.cargarSistemaArchivos(RUTA_FS);
        r.verificar("solo el del sistema de archivos: todavía no se arma (falta el de procesos)", !carga.armarConProcesos(log).armado()
                && "Falta cargar el JSON de procesos".equals(carga.motivoNoArmar()), carga.motivoNoArmar());
        r.verificar("estado del sistema de archivos: 10 entradas, 200 bloques, cabezal en 50", DialogoCarga.textoSistema(fs)
                .contains("10 entradas") && DialogoCarga.textoSistema(fs).contains("200 bloques")
                && DialogoCarga.textoSistema(fs).contains("cabezal en 50"), DialogoCarga.textoSistema(fs));
        DatosProcesos procesos = carga.cargarProcesos(RUTA_PROCESOS);
        r.verificar("estado de procesos: 8 procesos, RAM 1024, FIFO, swap 100", DialogoCarga.textoProcesos(procesos)
                .contains("8 procesos") && DialogoCarga.textoProcesos(procesos).contains("RAM 1024")
                && DialogoCarga.textoProcesos(procesos).contains("FIFO") && DialogoCarga.textoProcesos(procesos).contains("swap 100"),
                DialogoCarga.textoProcesos(procesos));
        CargaSimulacion.Resultado resultado = carga.armarConProcesos(log);
        r.verificar("con los dos: se arma con 1 computador y sus 8 procesos, IDs desde 1", resultado.armado()
                && resultado.cluster().cantidadComputadores() == 1 && resultado.cluster().todosLosProcesos().tamano() == 8
                && resultado.cluster().todosLosProcesos().primero().getId() == 1, "");
        r.verificar("un JSON que no existe no reemplaza al bueno", !carga.cargarProcesos("config/no-existe.json").cargable()
                && carga.motivoNoArmar() == null, "");
        resultado.cluster().getComputador(0).getSistemaArchivos().renombrar(ADMIN, "/usuarios/data.csv", "datos.csv");
        CargaSimulacion otra = new CargaSimulacion();
        otra.usarProcesos(carga.getProcesos());
        r.verificar("conservar el disco actual: el rearmado trae el archivo renombrado",
                otra.usarEstadoActual(resultado.cluster().getComputador(0))
                && otra.armarConProcesos(log).cluster().getComputador(0).getSistemaArchivos().existe("/usuarios/datos.csv"), "");
        resultado.cluster().apagar();
        r.verificar("sin ERROR (los JSON inexistentes son INFO en la carga)", Debug.getErrores() - errores <= 1,
                (Debug.getErrores() - errores) + " ERROR");
    }

    /** Las colas se copian bajo el semáforo del Kernel, incluidas las dos de suspendidos, con la operación de cada proceso. */
    private static void probarFotosDeColas() {
        r.grupo("Foto segura de las siete colas");
        Cluster c = armado();
        FotoComputador f = FotoComputador.de(c.getComputador(0));
        int total = f.nuevos().length + f.listos().length + f.bloqueados().length + f.terminados().length
                + f.listosSuspendidos().length + f.bloqueadosSuspendidos().length + (f.enCPU() == null ? 0 : 1);
        r.verificar("antes de iniciar: los 8 procesos están en la foto", total == 8, total + "");
        r.verificar("las colas de suspendidos existen (vacías con RAM 1024)", f.listosSuspendidos().length == 0
                && f.bloqueadosSuspendidos().length == 0, "");
        FilaProceso primero = f.nuevos().length > 0 ? f.nuevos()[0] : f.listos()[0];
        r.verificar("cada fila sabe su operación de E/S: 'Leer /sistema/config.sys'", primero.esDeArchivo()
                && "Leer /sistema/config.sys".equals(primero.operacionES()), primero.operacionES());
        r.verificar("en RAM (no suspendido): bloque de swap -1", primero.bloqueSwap() == -1, "");
        c.getReloj().ejecutarCiclos(30);
        FotoComputador enMarcha = FotoComputador.de(c.getComputador(0));
        boolean bloqueadoConMotivo = false;
        for (FilaProceso p : enMarcha.bloqueados()) {
            bloqueadoConMotivo |= p.motivo().contains("E/S") && p.motivo().contains("disco");
        }
        r.verificar("a los 30 ciclos hay procesos Bloqueados con su motivo (E/S: esperando el disco)", bloqueadoConMotivo, "");
        r.verificar("buscar por ID en todas las colas", enMarcha.buscar(primero.id()) != null && enMarcha.buscar(999) == null, "");
        terminar(c);
        FotoComputador fin = FotoComputador.de(c.getComputador(0));
        r.verificar("al final los 8 están en Terminados", fin.terminados().length == 8, fin.terminados().length + "");
        c.apagar();
    }

    /** El mapa sabe quién ocupa cada bloque; el cabezal, la política y la cola de E/S salen de la foto. */
    private static void probarFotoDisco() {
        r.grupo("Foto del disco: mapa, ocupantes, cabezal y cola");
        Cluster c = armado();
        FotoDisco d = FotoDisco.de(c.getComputador(0), 0);
        InfoEntrada boot = c.getComputador(0).getSistemaArchivos().buscar("/sistema/boot.bin");
        r.verificar("300 posiciones: 200 de archivos y swap aparte", d.total() == 300 && d.bloquesArchivos() == 200, "");
        r.verificar("el bloque 11 es de /sistema/boot.bin (con su ID para el color)", "/sistema/boot.bin".equals(d.ocupante()[11])
                && d.idArchivo()[11] == boot.id(), d.ocupante()[11]);
        r.verificar("bloques libres y de swap libres se nombran como tales", "libre".equals(d.ocupante()[0])
                && "libre (swap)".equals(d.ocupante()[250]) && d.idArchivo()[0] == FotoDisco.SIN_ARCHIVO, "");
        r.verificar("cabezal en 50, política FIFO, 192 y 100 libres", d.cabezal() == 50 && d.politica() == PoliticaDiscoTipo.FIFO
                && d.libresArchivos() == 192 && d.libresSwap() == 100, "");
        r.verificar("cola vacía antes de iniciar", d.actual() == null && d.cola().length == 0, "");
        c.getReloj().ejecutarCiclos(30);
        FotoDisco enMarcha = FotoDisco.de(c.getComputador(0), c.getReloj().getCiclo());
        int solicitudes = enMarcha.cola().length + (enMarcha.actual() == null ? 0 : 1);
        r.verificar("a los 30 ciclos las 8 solicitudes están en el disco (una en atención)", solicitudes == 8 && enMarcha.actual() != null,
                solicitudes + "");
        r.verificar("la solicitud en atención marca su bloque en el mapa", enMarcha.esDeLaActual(enMarcha.actual().destino())
                && !enMarcha.esDeLaActual(0), "");
        r.verificar("cada solicitud dice su operación, ruta y cuánto lleva esperando", enMarcha.cola()[0].operacion() != null
                && enMarcha.cola()[0].ruta().startsWith("/") && enMarcha.cola()[0].espera() > 0, "");
        c.apagar();
    }

    /** La foto del árbol trae todo en preorden, los archivos para la tabla y los usuarios para el selector de modo. */
    private static void probarFotoArchivos() {
        r.grupo("Foto del sistema de archivos");
        Cluster c = armado();
        SistemaArchivos fs = c.getComputador(0).getSistemaArchivos();
        FotoArchivos f = FotoArchivos.de(fs);
        r.verificar("11 entradas (raíz + 2 directorios + 8 archivos) y la raíz primero", f.entradas().length == 11
                && SistemaArchivos.RAIZ.equals(f.entradas()[0].ruta()), "");
        r.verificar("8 filas para la tabla de asignación", f.archivos().length == 8, "");
        r.verificar("usuarios del selector: user1 y user2 (sin admin)", f.usuarios().length == 2
                && contiene(f.usuarios(), "user1") && contiene(f.usuarios(), "user2"), "");
        r.verificar("buscar por ruta", f.buscar("/usuarios/notas.txt") != null && f.buscar("/nada") == null, "");
        String antes = f.firma();
        fs.renombrar(ADMIN, "/sistema/core.dll", "nucleo.dll");
        r.verificar("la firma cambia al renombrar (el JTree se redibuja)", !antes.equals(FotoArchivos.de(fs).firma()), "");
        r.verificar("y no cambia si solo se lee (no se redibuja de más)", FotoArchivos.de(fs).firma().equals(fsFirmaTrasLeer(fs)), "");
        r.verificar("sin sistema de archivos (Proyecto 1): foto nula", FotoArchivos.de(null) == null, "");
        c.apagar();
    }

    private static String fsFirmaTrasLeer(SistemaArchivos fs) {
        fs.leer(ADMIN, "/usuarios/notas.txt");
        return FotoArchivos.de(fs).firma();
    }

    /** Cada archivo tiene siempre el mismo color y el texto sobre él se lee. */
    private static void probarColores() {
        r.grupo("Colores de los archivos");
        r.verificar("mismo ID, mismo color", ColoresArchivo.de(7).equals(ColoresArchivo.de(7)), "");
        boolean distintos = true;
        for (int i = 0; i < ColoresArchivo.cantidad(); i++) {
            for (int j = i + 1; j < ColoresArchivo.cantidad(); j++) {
                distintos &= !ColoresArchivo.de(i).equals(ColoresArchivo.de(j));
            }
        }
        r.verificar(ColoresArchivo.cantidad() + " colores distintos antes de repetir", distintos, "");
        r.verificar("IDs negativos no fallan", ColoresArchivo.de(-3) != null, "");
        r.verificar("texto blanco sobre oscuro y oscuro sobre claro", Color.WHITE.equals(ColoresArchivo.textoSobre(new Color(20, 20, 20)))
                && !Color.WHITE.equals(ColoresArchivo.textoSobre(ColoresArchivo.LIBRE)), "");
    }

    /** El clic derecho ofrece cada opción habilitada o deshabilitada con el motivo, según el modo de usuario. */
    private static void probarOpcionesDelMenu() {
        r.grupo("Clic derecho: opciones y motivos por modo de usuario");
        Cluster c = armado();
        SistemaArchivos fs = c.getComputador(0).getSistemaArchivos();
        InfoEntrada notas = fs.buscar("/usuarios/notas.txt");
        OpcionesArchivo.Opcion[] admin = OpcionesArchivo.para(notas, ADMIN, fs);
        boolean todas = true;
        for (OpcionesArchivo.Opcion o : admin) {
            todas &= o.habilitada();
        }
        r.verificar("archivo: leer, actualizar, renombrar y eliminar; el administrador puede todo", admin.length == 4 && todas, "");
        OpcionesArchivo.Opcion[] u1 = OpcionesArchivo.para(notas, USER1, fs);
        r.verificar("user1 sobre su notas.txt: lee, actualiza y elimina", habilitada(u1, AccionArchivo.LEER)
                && habilitada(u1, AccionArchivo.ACTUALIZAR) && habilitada(u1, AccionArchivo.ELIMINAR), "");
        r.verificar("user1 no renombra: 'solo el administrador'", motivo(u1, AccionArchivo.RENOMBRAR).contains("solo el administrador"),
                motivo(u1, AccionArchivo.RENOMBRAR));
        OpcionesArchivo.Opcion[] u2 = OpcionesArchivo.para(notas, USER2, fs);
        r.verificar("user2 no lee notas.txt: 'privado de user1'", motivo(u2, AccionArchivo.LEER).contains("privado de user1"),
                motivo(u2, AccionArchivo.LEER));
        OpcionesArchivo.Opcion[] dir = OpcionesArchivo.para(fs.buscar("/usuarios"), USER1, fs);
        r.verificar("directorio: crear archivo, crear directorio, renombrar y eliminar", dir.length == 4
                && OpcionesArchivo.buscar(dir, AccionArchivo.LEER) == null, "");
        r.verificar("user1 no crea en /usuarios (de admin) y lo explica", motivo(dir, AccionArchivo.CREAR_ARCHIVO).contains("sus propios directorios")
                && motivo(dir, AccionArchivo.CREAR_DIRECTORIO).contains("solo el administrador"), "");
        OpcionesArchivo.Opcion[] raiz = OpcionesArchivo.para(fs.buscar("/"), ADMIN, fs);
        r.verificar("ni el administrador renombra o elimina la raíz", motivo(raiz, AccionArchivo.RENOMBRAR).contains("raíz")
                && motivo(raiz, AccionArchivo.ELIMINAR).contains("raíz") && habilitada(raiz, AccionArchivo.CREAR_DIRECTORIO), "");
        c.apagar();
    }

    /** El formulario valida tipo y rango de cada campo y arma los datos del proceso con la ruta correcta. */
    private static void probarFormulario() {
        r.grupo("Formulario del proceso de E/S: validación y datos");
        InfoEntrada notas = new InfoEntrada(4, "/usuarios/notas.txt", "notas.txt", avilaos.enums.TipoEntrada.ARCHIVO, "user1",
                Permiso.PRIVADO, 34, 1, "carga inicial", "Crear", 2);
        InfoEntrada sistema = new InfoEntrada(1, "/sistema", "sistema", avilaos.enums.TipoEntrada.DIRECTORIO, "admin",
                Permiso.PUBLICO, -1, 0, "carga inicial", "Crear", 1);
        InfoEntrada raiz = new InfoEntrada(0, "/", "/", avilaos.enums.TipoEntrada.DIRECTORIO, "admin", Permiso.PUBLICO, -1, 0,
                "sistema", "Crear", 0);
        DialogoOperacionArchivo.Armado leer = armar(AccionArchivo.LEER, notas, "Leer_notas", "16", "500", "2", "", "");
        r.verificar("leer: operación READ sobre la ruta, 0 bloques, sin nombre nuevo", leer.valido()
                && leer.datos().operacion() == TipoOperacionES.LEER && "/usuarios/notas.txt".equals(leer.datos().ruta())
                && leer.datos().bloquesArchivo() == 0 && leer.datos().nuevoNombre() == null, leer.motivo());
        DialogoOperacionArchivo.Armado crear = armar(AccionArchivo.CREAR_ARCHIVO, sistema, "Crear", "16", "500", "2", "log.txt", "3");
        r.verificar("crear archivo en /sistema: ruta /sistema/log.txt y 3 bloques", crear.valido()
                && "/sistema/log.txt".equals(crear.datos().ruta()) && crear.datos().bloquesArchivo() == 3, crear.motivo());
        DialogoOperacionArchivo.Armado enRaiz = armar(AccionArchivo.CREAR_DIRECTORIO, raiz, "Crear", "16", "500", "2", "datos", "");
        r.verificar("crear directorio en la raíz: ruta /datos y 0 bloques", enRaiz.valido() && "/datos".equals(enRaiz.datos().ruta())
                && enRaiz.datos().bloquesArchivo() == 0, enRaiz.motivo());
        DialogoOperacionArchivo.Armado renombrar = armar(AccionArchivo.RENOMBRAR, notas, "Ren", "16", "500", "2", "apuntes.txt", "");
        r.verificar("renombrar: UPDATE con nombre nuevo", renombrar.valido() && renombrar.datos().operacion() == TipoOperacionES.ACTUALIZAR
                && "apuntes.txt".equals(renombrar.datos().nuevoNombre()), renombrar.motivo());
        r.verificar("nombre del proceso vacío", motivo(armar(AccionArchivo.LEER, notas, "  ", "16", "500", "2", "", "")).contains("vacío"), "");
        r.verificar("memoria 0 y memoria mayor que la RAM", motivo(armar(AccionArchivo.LEER, notas, "P", "0", "500", "2", "", "")).contains("Memoria")
                && motivo(armar(AccionArchivo.LEER, notas, "P", "2000", "500", "2", "", "")).contains("fuera del rango"), "");
        r.verificar("deadline que no es número", motivo(armar(AccionArchivo.LEER, notas, "P", "16", "abc", "2", "", "")).contains("no es un número"), "");
        r.verificar("más bloques que los libres", motivo(armar(AccionArchivo.CREAR_ARCHIVO, sistema, "P", "16", "500", "2", "a", "500"))
                .contains("bloques libres en el disco: 192"), "");
        r.verificar("disco sin bloques libres", motivo(DialogoOperacionArchivo.armar(AccionArchivo.CREAR_ARCHIVO, sistema, "P", "16", "500",
                "2", 5, "a", "1", 1024, 0)).contains("No hay bloques libres"), "");
        r.verificar("nombre nuevo inválido (vacío o con barra)", motivo(armar(AccionArchivo.RENOMBRAR, notas, "P", "16", "500", "2", "", "")).startsWith("Nombre")
                && motivo(armar(AccionArchivo.CREAR_ARCHIVO, sistema, "P", "16", "500", "2", "a/b", "1")).startsWith("Nombre"), "");
        String sugerido = DialogoOperacionArchivo.nombreSugerido(AccionArchivo.ACTUALIZAR, notas);
        r.verificar("nombre sugerido corto y con caracteres válidos: " + sugerido, sugerido.length() <= 24 && !sugerido.contains("."), sugerido);
    }

    /** Un proceso creado desde el formulario pasa por la CPU, el disco y aplica el cambio al terminar. */
    private static void probarProcesoDesdeLaInterfaz() {
        r.grupo("Proceso creado desde la interfaz, de punta a punta");
        long errores = Debug.getErrores();
        Cluster c = armado();
        SistemaArchivos fs = c.getComputador(0).getSistemaArchivos();
        DatosProcesoArchivo dir = armar(AccionArchivo.CREAR_DIRECTORIO, fs.buscar("/usuarios"), "Crear_user1", "16", "500", "2", "user1", "").datos();
        Usuario actuante = DialogoOperacionArchivo.actuante(ADMIN, "user1");
        r.verificar("el administrador crea /usuarios/user1 para user1 (proceso de E/S)", c.validarProcesoArchivo(dir, actuante, Permiso.PRIVADO) == null
                && c.crearProcesoArchivo(dir, actuante, Permiso.PRIVADO) != null, "");
        terminar(c);
        r.verificar("al terminar el proceso existe /usuarios/user1 y es de user1", fs.existe("/usuarios/user1")
                && "user1".equals(fs.buscar("/usuarios/user1").dueno()), "");
        r.verificar("y en modo usuario, user1 ya puede crear archivos ahí", fs.motivoCrear(USER1, "/usuarios/user1",
                avilaos.enums.TipoEntrada.ARCHIVO) == null, "");
        r.verificar("el actuante conserva los permisos de administrador y el nombre del dueño",
                DialogoOperacionArchivo.actuante(ADMIN, "user1").esAdmin() && "user1".equals(DialogoOperacionArchivo.actuante(ADMIN, "user1").nombre())
                && DialogoOperacionArchivo.actuante(USER2, "user1").equals(USER2), "");
        InfoEntrada propio = fs.buscar("/usuarios/user1");
        int antes = FotoArchivos.de(fs).archivos().length;
        DatosProcesoArchivo archivo = armar(AccionArchivo.CREAR_ARCHIVO, propio, "Crear_diario", "16", "500", "2", "diario.txt", "2").datos();
        boolean creado = c.crearProcesoArchivo(archivo, ADMIN, Permiso.PRIVADO) != null;
        terminar(c);
        r.verificar("y un archivo de 2 bloques dentro, que suma una fila a la tabla de asignación", creado
                && fs.existe("/usuarios/user1/diario.txt") && FotoArchivos.de(fs).archivos().length == antes + 1,
                antes + " -> " + FotoArchivos.de(fs).archivos().length);
        r.verificar("sin ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Modelos de las tablas nuevas: columnas, filas y que no se refresquen sin cambios. */
    private static void probarModelos() {
        r.grupo("Modelos de la tabla de asignación y de la cola de E/S");
        Cluster c = armado();
        FotoArchivos f = FotoArchivos.de(c.getComputador(0).getSistemaArchivos());
        ModeloTablaAsignacion asignacion = new ModeloTablaAsignacion();
        AtomicInteger avisos = new AtomicInteger();
        asignacion.addTableModelListener(e -> avisos.incrementAndGet());
        asignacion.setFilas(f.archivos());
        r.verificar("8 columnas (color, archivo, bloques, primer bloque, dueño, permiso, creador, última op.)",
                asignacion.getColumnCount() == 8 && "Primer bloque".equals(asignacion.getColumnName(3))
                && "Creado por".equals(asignacion.getColumnName(6)), "");
        r.verificar("8 filas; la primera columna es el color del archivo", asignacion.getRowCount() == 8
                && asignacion.getColumnClass(0) == Color.class && asignacion.getValueAt(0, 0) instanceof Color, "");
        r.verificar("boot.bin: 1 bloque, primer bloque 11, dueño admin", "/sistema/boot.bin".equals(asignacion.getValueAt(0, 1))
                && "1".equals(asignacion.getValueAt(0, 2)) && "11".equals(asignacion.getValueAt(0, 3)) && "admin".equals(asignacion.getValueAt(0, 4)), "");
        asignacion.setFilas(FotoArchivos.de(c.getComputador(0).getSistemaArchivos()).archivos());
        r.verificar("mismos datos: no avisa a la tabla (no pierde la selección)", avisos.get() == 1, avisos.get() + "");
        c.getReloj().ejecutarCiclos(30);
        FotoDisco d = FotoDisco.de(c.getComputador(0), c.getReloj().getCiclo());
        ModeloColaDisco cola = new ModeloColaDisco();
        cola.setFilas(d.actual(), d.cola());
        r.verificar("cola de E/S: la solicitud en atención primero y luego las 7 en cola", cola.getRowCount() == 8
                && "en atención".equals(cola.getValueAt(0, 0)) && "en cola".equals(cola.getValueAt(1, 0)), cola.getRowCount() + "");
        r.verificar("cada fila muestra proceso, operación, ruta y bloque destino", cola.getValueAt(1, 2).toString().startsWith("P")
                && !cola.getValueAt(1, 3).toString().isEmpty() && cola.getValueAt(1, 4).toString().startsWith("/"), "");
        c.apagar();
    }

    /** Métricas del disco en la foto (solo cada 10 ciclos en marcha) y cambio de política desde la barra. */
    private static void probarMetricasYPolitica() {
        r.grupo("Métricas del disco y cambio de política desde la barra");
        Cluster c = armado();
        r.verificar("con el reloj quieto la foto trae las métricas del disco", FotoCluster.de(c).metricasDisco() != null, "");
        r.verificar("cambiar a SSTF desde la barra", c.cambiarPoliticaDisco(PoliticaDiscoTipo.SSTF)
                && c.getComputador(0).getPlanificadorDisco().getPolitica() == PoliticaDiscoTipo.SSTF, "");
        terminar(c);
        MetricasDisco m = FotoCluster.de(c).metricasDisco();
        String[][] filas = PanelMetricasDisco.filas(m);
        r.verificar("tabla de métricas: 14 filas (incluye las 6 políticas)", filas.length == 14, filas.length + "");
        r.verificar("8 atendidas, todas con SSTF", m.atendidas() == 8 && m.atendidasPor(PoliticaDiscoTipo.SSTF) == 8, "");
        r.verificar("decimales con coma: 52,375 -> 52,38 y 0 -> 0,00", "52,38".equals(PanelMetricasDisco.decimal(52.375))
                && "0,00".equals(PanelMetricasDisco.decimal(0)), PanelMetricasDisco.decimal(52.375));
        r.verificar("política nula -> ERROR", conError(() -> !c.cambiarPoliticaDisco(null)), "");
        c.apagar();
    }

    /** Fotos tomadas desde otro hilo mientras el reloj corre: sin excepciones ni ERROR (lecturas bajo semáforo). */
    private static void probarFotosConcurrentes() {
        r.grupo("Fotos desde otro hilo con el reloj en marcha");
        long errores = Debug.getErrores();
        Cluster c = armado();
        AtomicBoolean seguir = new AtomicBoolean(true);
        AtomicInteger fotos = new AtomicInteger();
        AtomicInteger excepciones = new AtomicInteger();
        Thread lector = new Thread(() -> {
            while (seguir.get()) {
                try {
                    FotoCluster f = FotoCluster.de(c);
                    if (f.disco() != null && f.archivos() != null) {
                        fotos.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    excepciones.incrementAndGet();
                }
            }
        }, "Prueba-Fotos-GUI");
        lector.start();
        terminar(c);
        seguir.set(false);
        try {
            lector.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        r.verificar("se tomaron fotos mientras corría (" + fotos.get() + ")", fotos.get() > 0, "");
        r.verificar("sin excepciones", excepciones.get() == 0, excepciones.get() + "");
        r.verificar("sin ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Sistema armado con los dos JSON del enunciado y sus 8 procesos. */
    private static Cluster armado() {
        CargaSimulacion carga = new CargaSimulacion();
        carga.cargarSistemaArchivos(RUTA_FS);
        carga.cargarProcesos(RUTA_PROCESOS);
        return carga.armarConProcesos(log).cluster();
    }

    /** Corre ciclos hasta que todos terminan (tope 3000). */
    private static void terminar(Cluster c) {
        int vueltas = 0;
        while (!(c.getReloj().getCiclo() > 0 && c.todosTerminados()) && vueltas++ < 300) {
            c.getReloj().ejecutarCiclos(10);
        }
    }

    private static DialogoOperacionArchivo.Armado armar(AccionArchivo accion, InfoEntrada sobre, String nombre, String memoria,
            String deadline, String instrucciones, String nuevo, String bloques) {
        return DialogoOperacionArchivo.armar(accion, sobre, nombre, memoria, deadline, instrucciones, 5, nuevo, bloques, 1024, 192);
    }

    private static String motivo(DialogoOperacionArchivo.Armado a) {
        return a.valido() ? "" : a.motivo();
    }

    /** Indica si el arreglo tiene ese texto (por contenido). */
    private static boolean contiene(String[] textos, String buscado) {
        for (String t : textos) {
            if (t.equals(buscado)) {
                return true;
            }
        }
        return false;
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

    private static boolean habilitada(OpcionesArchivo.Opcion[] opciones, AccionArchivo accion) {
        OpcionesArchivo.Opcion o = OpcionesArchivo.buscar(opciones, accion);
        return o != null && o.habilitada();
    }

    private static String motivo(OpcionesArchivo.Opcion[] opciones, AccionArchivo accion) {
        OpcionesArchivo.Opcion o = OpcionesArchivo.buscar(opciones, accion);
        return o == null ? "(no se ofrece)" : o.motivo();
    }
}
