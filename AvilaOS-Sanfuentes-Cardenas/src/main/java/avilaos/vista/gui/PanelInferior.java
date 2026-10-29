package avilaos.vista.gui;

import avilaos.log.Debug;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

/** Zona inferior redimensionable con una pestaña por vista global: buffers, log, consola, métricas y gráfico. */
public class PanelInferior extends JTabbedPane {

    private final PanelBuffers buffers = new PanelBuffers();
    private final PanelTexto log = new PanelTexto(200, FotoCluster.LINEAS_LOG, null);
    private final PanelTexto consola = new PanelTexto(200, FotoCluster.LINEAS_CONSOLA, "Mostrar salida del Debug");

    /** Arma las pestañas; las de métricas y gráfico se llenan en su parte correspondiente. */
    public PanelInferior() {
        addTab("Buffers", buffers);
        addTab("Log", log);
        addTab("Consola", consola);
        agregar("Métricas", "Métricas por computador y globales (parte 7)");
        agregar("Gráfico", "Utilización de CPU en el tiempo (parte 7)");
        consola.getCasilla().setToolTipText("Requiere \"debugger\": true en la configuración");
        consola.getCasilla().addActionListener(e -> alternarConsola());
    }

    /** Dibuja la foto en las pestañas de buffers, log y consola. */
    public void mostrar(FotoCluster foto) {
        buffers.mostrar(foto.buffers());
        log.mostrar(foto.log());
        if (consola.getCasilla().isSelected() && Debug.isActivo()) {
            consola.mostrar(foto.consola());
        }
    }

    private void alternarConsola() {
        boolean activa = consola.getCasilla().isSelected();
        if (activa && !Debug.isActivo()) {
            consola.mostrar(new String[]{"El debugger está apagado: pon \"debugger\": true en config/avilaosconfigs.json"});
        }
        Debug.getConsola().setActiva(activa);
        if (!activa) {
            consola.limpiar();
        }
    }

    private void agregar(String titulo, String pendiente) {
        JLabel aviso = new JLabel(pendiente, SwingConstants.CENTER);
        aviso.setForeground(new Color(108, 117, 125));
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(aviso, java.awt.BorderLayout.CENTER);
        addTab(titulo, panel);
    }
}
