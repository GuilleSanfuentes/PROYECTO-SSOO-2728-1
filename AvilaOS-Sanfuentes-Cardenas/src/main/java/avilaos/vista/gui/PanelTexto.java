package avilaos.vista.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Área de texto de solo lectura con auto-scroll, límite de líneas y casilla opcional, para el log y la consola. */
public class PanelTexto extends JPanel {

    private final JTextArea area = new JTextArea();
    private final JCheckBox autoScroll = new JCheckBox("Auto-scroll", true);
    private final JCheckBox activar;
    private final CampoEditable campoLimite = new CampoEditable(4);
    private final JLabel error = new JLabel();
    private final int maximo;
    private int limite;
    private String[] ultimas = new String[0];

    /** Crea el panel; si casilla no es null agrega esa casilla de activación, inicialmente desmarcada. */
    public PanelTexto(int limiteInicial, int maximo, String casilla) {
        super(new BorderLayout());
        this.limite = limiteInicial;
        this.maximo = maximo;
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        activar = casilla == null ? null : new JCheckBox(casilla, false);
        if (activar != null) {
            barra.add(activar);
        }
        barra.add(autoScroll);
        barra.add(new JLabel("Líneas máx.:"));
        barra.add(campoLimite);
        error.setForeground(ColoresEstado.ROJO);
        barra.add(error);
        campoLimite.mostrarValor(String.valueOf(limite));
        campoLimite.addActionListener(e -> aplicarLimite());
        add(barra, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    /** Casilla de activación; null si el panel no tiene. */
    public JCheckBox getCasilla() {
        return activar;
    }

    /** Muestra las últimas líneas de la foto, respetando el límite; solo redibuja si algo cambió. */
    public void mostrar(String[] lineas) {
        boolean igual = lineas.length == ultimas.length
                && (lineas.length == 0 || lineas[lineas.length - 1].equals(ultimas[ultimas.length - 1])
                && lineas[0].equals(ultimas[0]));
        ultimas = lineas;
        if (!igual) {
            redibujar();
        }
    }

    /** Vacía el área. */
    public void limpiar() {
        ultimas = new String[0];
        area.setText("");
    }

    private void redibujar() {
        int desde = Math.max(0, ultimas.length - limite);
        StringBuilder sb = new StringBuilder();
        for (int i = desde; i < ultimas.length; i++) {
            sb.append(ultimas[i]).append('\n');
        }
        area.setText(sb.toString());
        if (autoScroll.isSelected()) {
            area.setCaretPosition(area.getDocument().getLength());
        }
    }

    private void aplicarLimite() {
        Validador.ResultadoEntero r = Validador.entero(campoLimite.getText(), "Líneas máx.", 1, maximo);
        if (!r.valido()) {
            error.setText(r.motivo());
            return;
        }
        error.setText("");
        limite = r.valor();
        campoLimite.confirmar();
        redibujar();
    }
}
