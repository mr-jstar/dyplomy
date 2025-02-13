/*
 * -jaki procent obrazów jest zliczona
 * -czy na pewno chcemy coś zapisać czy coż samknąć
 * 
 */
package gui;

import imageProcessing.Sharpener;
import imageProcessing.ContrastFilter;
import imageProcessing.GaussianByConvolve;
import imageProcessing.HistogramEqualizationFilter;
import imageProcessing.Laplace;
import imageProcessing.Negative;
import mydicom.DicomFileContent;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import mydicom.DicomTools;
import mydicom.HU2GrayMapper;
import mydicom.HU2RGBMapperByJstar;
import mydicom.HU2RGBMapperBySilverstein;
import mydicom.PixelDataMapper;
import mydicom.RTG2GrayMapper;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author Małgorzata
 */
public class DicomExplorer extends javax.swing.JFrame {

    private final int NORMAL_4_BRIGHTNESS_SLIDER = 100;
    private final int NORMAL_4_CONTRAST_SLIDER = 100;
    private final int NORMAL_4_ZOOM_SLIDER = 100;

    private final String rcPath = System.getProperty("user.home") + File.separator + ".dicomexplorerrc";
    private String initialPath = ""; // serializuje to w Uniksie/Linuksie

    private File file;
    private int currentImg = -1;
    private final IconCellRenderer listRenderer = new IconCellRenderer();
    private ZoomSliderListener zoomer;
    private final ImageManager iManager;
    private BufferedImageOp brightness;
    private BufferedImageOp equalizer;
    private BufferedImageOp gaussian;
    private BufferedImageOp negative;
    private BufferedImageOp sharpen;
    private BufferedImageOp laplace;
    private final Map<JRadioButtonMenuItem, PixelDataMapper> colorMappers = new HashMap<>();
    private final ArrayList<DicomFileContent> selectedFiles = new ArrayList<>();

    public DicomExplorer() {
        initComponents();

        iManager = new ImageManager(imageHolder);
        zoomer = new ZoomSliderListener(iManager);
        zoomSlider.addChangeListener(zoomer);

        Hashtable labels = new Hashtable();
        labels.put(NORMAL_4_ZOOM_SLIDER / 5, new JLabel("1:5"));
        labels.put(NORMAL_4_ZOOM_SLIDER, new JLabel("1:1"));
        labels.put(NORMAL_4_ZOOM_SLIDER * 3, new JLabel("3:1"));
        labels.put(NORMAL_4_ZOOM_SLIDER * 5, new JLabel("5:1"));
        zoomSlider.setLabelTable(labels);

        labels = new Hashtable();
        labels.put(brightnessSlider.getMinimum(), new JLabel("-"));
        labels.put(brightnessSlider.getMaximum(), new JLabel("+"));
        brightnessSlider.setLabelTable(labels);
        labels = new Hashtable();
        labels.put(contrastSlider.getMinimum(), new JLabel("-"));
        labels.put(contrastSlider.getMaximum(), new JLabel("+"));
        contrastSlider.setLabelTable(labels);

        fileList.setCellRenderer(listRenderer);
        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent lse) {//metoda wartość zmian(argument lista wybranych wydarzeń)
                currentImg = fileList.getSelectedIndex();//obiekt JList pobiera wybrane wartości

                if (currentImg >= 0) {
                    BufferedImage img = fileList.getModel().getElementAt(currentImg).getImage();
                    //System.out.println( "Drawing " + img.getWidth() + "x" + img.getHeight());
                    iManager.updateImg(img);
                    iManager.repaint(zoomer.getCurrentScale());
                    patientData.setText(fileList.getModel().getElementAt(currentImg).getData());
                    pixelData.setText("");
                }
            }
        });

        fileList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == 's') {
                    DicomFileContent f = fileList.getModel().getElementAt(fileList.getSelectedIndex());
                    selectedFiles.add(f);
                }
                if (e.getKeyChar() == 'x') {
                    DicomFileContent f = fileList.getModel().getElementAt(fileList.getSelectedIndex());
                    selectedFiles.remove(f);
                }
                if (e.getKeyChar() == 'Q') {
                    selectedFiles.clear();
                }
                if (e.getKeyChar() == 'O') {
                    Collections.sort(selectedFiles);
                }
                if (e.getKeyChar() == 'P') {
                    for (DicomFileContent f : selectedFiles) {
                        System.out.println(f.getName());
                    }
                }
            }
        });
        fileList.setToolTipText("<html>"
                + "s - add file to selection<br>"
                + "x - remove file from selection<br>"
                + "Q - clear selection<br>"
                + "O - sort selection (by slice)<br>"
                + "P - print selection to stdout"
                + "</html>");

        colorMappers.put(silversteinCMItem, new HU2RGBMapperBySilverstein());
        colorMappers.put(jstarCMItem, new HU2RGBMapperByJstar());
        colorMappers.put(grayscaleCMItem, new HU2GrayMapper());
        colorMappers.put(rtgCMItem, new RTG2GrayMapper());

        MouseAdapter ma = new MouseAdapter() {

            private Point origin;

            @Override
            public void mouseEntered(MouseEvent e) {
                imageHolder.requestFocusInWindow();  // dzięki temu działa obsługa klawiszy +/-
            }

            @Override
            public void mousePressed(MouseEvent e) {
                origin = new Point(e.getPoint());
                if (e.getButton() == MouseEvent.BUTTON3) {
                    //System.out.println(origin);
                    Icon icon = imageHolder.getIcon();
                    if (icon != null) {
                        //System.out.println(icon.getIconWidth() + "x" + icon.getIconHeight());
                        JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, imageHolder);
                        if (viewPort != null) {
                            Rectangle view = viewPort.getViewRect();
                            //System.out.println(view);
                            int imgX, imgY;
                            if (view.getMinX() == 0) {
                                imgX = icon.getIconWidth() < view.getMaxX() ? origin.x - ((int) view.getMaxX() - icon.getIconWidth()) / 2 : origin.x;
                            } else {
                                imgX = origin.x;// + (int) view.getMinX();
                            }
                            if (view.getMinY() == 0) {
                                imgY = icon.getIconHeight() < view.getMaxY() ? origin.y - ((int) view.getMaxY() - icon.getIconHeight()) / 2 : origin.y;
                            } else {
                                imgY = origin.y;// + (int) view.getMinY();
                            }
                            //System.out.println(imgX + "," + imgY);
                            if (imgX > 0 && imgY > 0 && currentImg >= 0) {
                                DicomFileContent fc = fileList.getModel().getElementAt(currentImg);
                                imgX = (int) ((double) imgX / icon.getIconWidth() * fc.getWidth());
                                imgY = (int) ((double) imgY / icon.getIconHeight() * fc.getHeight());
                                pixelData.setText("Pixel Data=" + fc.getPixelData(imgX, imgY) + " @" + imgX + "," + imgY + "    ");
                            }
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (origin != null) {
                    JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, imageHolder);
                    if (viewPort != null) {
                        int deltaX = origin.x - e.getX();
                        int deltaY = origin.y - e.getY();

                        Rectangle view = viewPort.getViewRect();
                        view.x += deltaX;
                        view.y += deltaY;

                        imageHolder.scrollRectToVisible(view);
                    }
                }
            }

        };

        imageHolder.setAutoscrolls(true);
        imageHolder.addMouseListener(ma);
        imageHolder.addMouseMotionListener(ma);

        imageHolder.setFocusable(true);  // uwaga - samo to nie wystarczy, trzeba jeszcze dodać imageHolder.requestFocusInWindow() wyżej, w obsłudze myszy
        imageHolder.setFocusTraversalKeysEnabled(false);
        imageHolder.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char keyChar = e.getKeyChar();
                //System.err.println("key typed " + e.getKeyChar() + "->" + e.getKeyCode());
                if (keyChar == '+') {
                    zoomSlider.setValue(zoomSlider.getValue() + zoomSlider.getMaximum() / 10);
                    zoomer.stateChanged(new ChangeEvent(zoomSlider));
                } else if (keyChar == '-') {
                    zoomSlider.setValue(zoomSlider.getValue() - zoomSlider.getMaximum() / 10);
                    zoomer.stateChanged(new ChangeEvent(zoomSlider));
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                //System.err.println("key pressed " + e.getKeyChar() + "->" + e.getKeyCode());
                int keyCode = e.getKeyCode();
                if (e.isActionKey()) {
                    JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, imageHolder);
                    if (viewPort != null) {
                        Rectangle view = viewPort.getViewRect();
                        switch (keyCode) {
                            case KeyEvent.VK_LEFT:
                                view.x -= 20;
                                imageHolder.scrollRectToVisible(view);
                                break;
                            case KeyEvent.VK_RIGHT:
                                view.x += 20;
                                imageHolder.scrollRectToVisible(view);
                                break;
                            case KeyEvent.VK_UP:
                                view.y -= 20;
                                imageHolder.scrollRectToVisible(view);
                                break;
                            case KeyEvent.VK_DOWN:
                                view.y += 20;
                                imageHolder.scrollRectToVisible(view);
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        });
        imageHolder.setToolTipText("<html>"
                + "Use \"+\" - zoom in<br>"
                + "Use \"-\" - zoom out<br>"
                + "Drag Mouse + Left Key - move<br>"
                + "Arrows - move<br>"
                + "Right Mouse Key to Pick pixel data"
                + "</html>");

        fileList.setModel(new DefaultListModel<DicomFileContent>());
        deSerializeState();

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        filePanel = new javax.swing.JPanel();
        fileListScroll = new javax.swing.JScrollPane();
        fileList = new javax.swing.JList<>();
        patientData = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        filesTitle = new javax.swing.JLabel();
        sortFilesButton = new javax.swing.JButton();
        clearFilesButton = new javax.swing.JButton();
        mainPanel = new javax.swing.JPanel();
        controlPanel = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        zoomPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        zoomSlider = new javax.swing.JSlider(new DefaultBoundedRangeModel(100, 0,100,150));
        zoomInit = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        contrastPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        brightnessSlider = new javax.swing.JSlider(new DefaultBoundedRangeModel(100, 0,100,150));
        jLabel4 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        contrastSlider = new javax.swing.JSlider();
        imgScroll = new javax.swing.JScrollPane();
        imageHolder = new javax.swing.JLabel();
        statusPanel = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        status = new javax.swing.JLabel();
        pixelData = new javax.swing.JLabel();
        rightPanel = new javax.swing.JPanel();
        leftPanel = new javax.swing.JPanel();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openFileMenuItem = new javax.swing.JMenuItem();
        openDirMenuItem = new javax.swing.JMenuItem();
        separator1 = new javax.swing.JPopupMenu.Separator();
        savePNGMenuItem = new javax.swing.JMenuItem();
        separator2 = new javax.swing.JPopupMenu.Separator();
        exitMenuItem = new javax.swing.JMenuItem();
        filterMenu = new javax.swing.JMenu();
        histEqualizationItem = new javax.swing.JMenuItem();
        gaussianFilterItem = new javax.swing.JMenuItem();
        negativeItem = new javax.swing.JMenuItem();
        sharpenerMenuItem = new javax.swing.JMenuItem();
        laplaceMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        switchListViewItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        silversteinCMItem = new javax.swing.JRadioButtonMenuItem();
        jstarCMItem = new javax.swing.JRadioButtonMenuItem();
        grayscaleCMItem = new javax.swing.JRadioButtonMenuItem();
        rtgCMItem = new javax.swing.JRadioButtonMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setBackground(new java.awt.Color(0, 0, 0));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        filePanel.setMaximumSize(new java.awt.Dimension(180, 2048));
        filePanel.setMinimumSize(new java.awt.Dimension(180, 140));
        filePanel.setPreferredSize(new java.awt.Dimension(180, 600));
        filePanel.setLayout(new java.awt.BorderLayout());

        fileListScroll.setBackground(new java.awt.Color(0, 0, 0));
        fileListScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        fileList.setBackground(new java.awt.Color(0, 0, 0));
        fileList.setForeground(new java.awt.Color(244, 244, 244));
        fileList.setMaximumSize(new java.awt.Dimension(100, 100));
        fileList.setMinimumSize(new java.awt.Dimension(100, 100));
        fileListScroll.setViewportView(fileList);

        filePanel.add(fileListScroll, java.awt.BorderLayout.CENTER);

        patientData.setBackground(new java.awt.Color(0, 0, 0));
        patientData.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        patientData.setAutoscrolls(true);
        patientData.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 2, 2));
        patientData.setMaximumSize(new java.awt.Dimension(180, 300));
        patientData.setMinimumSize(new java.awt.Dimension(180, 100));
        patientData.setPreferredSize(new java.awt.Dimension(180, 300));
        filePanel.add(patientData, java.awt.BorderLayout.SOUTH);

        jPanel1.setMaximumSize(new java.awt.Dimension(20, 32767));
        jPanel1.setMinimumSize(new java.awt.Dimension(20, 0));
        jPanel1.setPreferredSize(new java.awt.Dimension(20, 564));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 20, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        filePanel.add(jPanel1, java.awt.BorderLayout.WEST);

        jPanel3.setMaximumSize(new java.awt.Dimension(200, 29));
        jPanel3.setMinimumSize(new java.awt.Dimension(100, 29));
        jPanel3.setPreferredSize(new java.awt.Dimension(180, 29));

        filesTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        filesTitle.setText("Files");
        jPanel3.add(filesTitle);

        sortFilesButton.setFont(new java.awt.Font("Ubuntu", 0, 12)); // NOI18N
        sortFilesButton.setText("sort");
        sortFilesButton.setMaximumSize(new java.awt.Dimension(60, 20));
        sortFilesButton.setMinimumSize(new java.awt.Dimension(60, 20));
        sortFilesButton.setPreferredSize(new java.awt.Dimension(60, 20));
        sortFilesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortFilesButtonActionPerformed(evt);
            }
        });
        jPanel3.add(sortFilesButton);

        clearFilesButton.setFont(new java.awt.Font("Ubuntu", 0, 12)); // NOI18N
        clearFilesButton.setText("clear");
        clearFilesButton.setMaximumSize(new java.awt.Dimension(60, 20));
        clearFilesButton.setMinimumSize(new java.awt.Dimension(60, 20));
        clearFilesButton.setPreferredSize(new java.awt.Dimension(60, 20));
        clearFilesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearFilesButtonActionPerformed(evt);
            }
        });
        jPanel3.add(clearFilesButton);

        filePanel.add(jPanel3, java.awt.BorderLayout.NORTH);

        getContentPane().add(filePanel, java.awt.BorderLayout.WEST);

        mainPanel.setBackground(new java.awt.Color(0, 0, 0));
        mainPanel.setToolTipText("");
        mainPanel.setMaximumSize(new java.awt.Dimension(2200, 2500));
        mainPanel.setMinimumSize(new java.awt.Dimension(300, 400));
        mainPanel.setPreferredSize(new java.awt.Dimension(820, 720));
        mainPanel.setLayout(new java.awt.BorderLayout());

        controlPanel.setMaximumSize(new java.awt.Dimension(100, 80));
        controlPanel.setMinimumSize(new java.awt.Dimension(300, 80));
        controlPanel.setPreferredSize(new java.awt.Dimension(800, 80));

        jLabel5.setText("          ");
        controlPanel.add(jLabel5);

        zoomPanel.setLayout(new java.awt.BorderLayout());

        jLabel1.setText("Zoom:");
        zoomPanel.add(jLabel1, java.awt.BorderLayout.WEST);

        zoomSlider.setFont(new java.awt.Font("Ubuntu", 0, 15)); // NOI18N
        zoomSlider.setForeground(new java.awt.Color(0, 0, 0));
        zoomSlider.setMaximum(5*NORMAL_4_ZOOM_SLIDER);
        zoomSlider.setMinimum(NORMAL_4_ZOOM_SLIDER/5);
        zoomSlider.setMinorTickSpacing(NORMAL_4_ZOOM_SLIDER/5);
        zoomSlider.setPaintLabels(true);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setPaintTrack(false);
        zoomSlider.setToolTipText("");
        zoomSlider.setValue(NORMAL_4_ZOOM_SLIDER);
        zoomPanel.add(zoomSlider, java.awt.BorderLayout.CENTER);

        zoomInit.setText("1:1");
        zoomInit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInitActionPerformed(evt);
            }
        });
        zoomPanel.add(zoomInit, java.awt.BorderLayout.EAST);

        controlPanel.add(zoomPanel);

        jLabel3.setText("          ");
        controlPanel.add(jLabel3);

        jLabel2.setText("Brightness: ");
        contrastPanel.add(jLabel2);

        brightnessSlider.setForeground(new java.awt.Color(0, 0, 0));
        brightnessSlider.setMaximum(NORMAL_4_BRIGHTNESS_SLIDER);
        brightnessSlider.setMinimum(0);
        brightnessSlider.setPaintLabels(true);
        brightnessSlider.setPaintTicks(true);
        brightnessSlider.setToolTipText("");
        brightnessSlider.setValue(NORMAL_4_BRIGHTNESS_SLIDER);
        brightnessSlider.setAutoscrolls(true);
        brightnessSlider.setMaximumSize(new java.awt.Dimension(100, 62));
        brightnessSlider.setMinimumSize(new java.awt.Dimension(50, 62));
        brightnessSlider.setPreferredSize(new java.awt.Dimension(100, 62));
        brightnessSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                brightnessSliderStateChanged(evt);
            }
        });
        contrastPanel.add(brightnessSlider);

        controlPanel.add(contrastPanel);

        jLabel4.setText("          ");
        controlPanel.add(jLabel4);

        jLabel6.setText("Contrast");
        controlPanel.add(jLabel6);

        contrastSlider.setForeground(new java.awt.Color(0, 0, 0));
        contrastSlider.setMaximum(NORMAL_4_CONTRAST_SLIDER);
        contrastSlider.setMinimum(0);
        contrastSlider.setPaintLabels(true);
        contrastSlider.setPaintTicks(true);
        contrastSlider.setValue(NORMAL_4_CONTRAST_SLIDER);
        contrastSlider.setAutoscrolls(true);
        contrastSlider.setMaximumSize(new java.awt.Dimension(100, 62));
        contrastSlider.setMinimumSize(new java.awt.Dimension(50, 62));
        contrastSlider.setPreferredSize(new java.awt.Dimension(100, 62));
        contrastSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                contrastSliderStateChanged(evt);
            }
        });
        controlPanel.add(contrastSlider);

        mainPanel.add(controlPanel, java.awt.BorderLayout.NORTH);

        imgScroll.setBackground(new java.awt.Color(0, 0, 0));
        imgScroll.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        imgScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        imgScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        imgScroll.setAutoscrolls(true);
        imgScroll.setMaximumSize(new java.awt.Dimension(2080, 2080));
        imgScroll.setMinimumSize(new java.awt.Dimension(96, 96));
        imgScroll.setPreferredSize(new java.awt.Dimension(544, 544));

        imageHolder.setBackground(new java.awt.Color(0, 0, 0));
        imageHolder.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        imageHolder.setLabelFor(imageHolder);
        imageHolder.setMaximumSize(new java.awt.Dimension(2048, 2048));
        imageHolder.setMinimumSize(new java.awt.Dimension(64, 64));
        imageHolder.setOpaque(true);
        imgScroll.setViewportView(imageHolder);

        mainPanel.add(imgScroll, java.awt.BorderLayout.CENTER);

        statusPanel.setMaximumSize(new java.awt.Dimension(32767, 20));
        statusPanel.setMinimumSize(new java.awt.Dimension(0, 20));
        statusPanel.setPreferredSize(new java.awt.Dimension(512, 20));
        statusPanel.setLayout(new java.awt.BorderLayout());

        jPanel2.setPreferredSize(new java.awt.Dimension(20, 20));

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 20, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        statusPanel.add(jPanel2, java.awt.BorderLayout.WEST);

        status.setFont(new java.awt.Font("Ubuntu", 0, 12)); // NOI18N
        status.setText("Applied filters:");
        statusPanel.add(status, java.awt.BorderLayout.CENTER);

        pixelData.setText(" ");
        statusPanel.add(pixelData, java.awt.BorderLayout.EAST);

        mainPanel.add(statusPanel, java.awt.BorderLayout.SOUTH);

        rightPanel.setMaximumSize(new java.awt.Dimension(20, 32767));
        rightPanel.setMinimumSize(new java.awt.Dimension(20, 0));
        rightPanel.setPreferredSize(new java.awt.Dimension(20, 780));

        javax.swing.GroupLayout rightPanelLayout = new javax.swing.GroupLayout(rightPanel);
        rightPanel.setLayout(rightPanelLayout);
        rightPanelLayout.setHorizontalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 20, Short.MAX_VALUE)
        );
        rightPanelLayout.setVerticalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 748, Short.MAX_VALUE)
        );

        mainPanel.add(rightPanel, java.awt.BorderLayout.LINE_END);

        leftPanel.setMaximumSize(new java.awt.Dimension(20, 32767));
        leftPanel.setMinimumSize(new java.awt.Dimension(20, 0));
        leftPanel.setPreferredSize(new java.awt.Dimension(20, 780));

        javax.swing.GroupLayout leftPanelLayout = new javax.swing.GroupLayout(leftPanel);
        leftPanel.setLayout(leftPanelLayout);
        leftPanelLayout.setHorizontalGroup(
            leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 20, Short.MAX_VALUE)
        );
        leftPanelLayout.setVerticalGroup(
            leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 748, Short.MAX_VALUE)
        );

        mainPanel.add(leftPanel, java.awt.BorderLayout.LINE_START);

        getContentPane().add(mainPanel, java.awt.BorderLayout.CENTER);

        fileMenu.setText("File");

        openFileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        openFileMenuItem.setText("Open file");
        openFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openFileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openFileMenuItem);

        openDirMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.ALT_MASK));
        openDirMenuItem.setText("Open Directory");
        openDirMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openDirMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openDirMenuItem);
        fileMenu.add(separator1);

        savePNGMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        savePNGMenuItem.setText("Save(as png)");
        savePNGMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savePNGMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(savePNGMenuItem);
        fileMenu.add(separator2);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_MASK));
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        filterMenu.setText("Filters");

        histEqualizationItem.setText("Histogram Equalization");
        histEqualizationItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                histEqualizationItemActionPerformed(evt);
            }
        });
        filterMenu.add(histEqualizationItem);

        gaussianFilterItem.setText("Gaussian Blur");
        gaussianFilterItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gaussianFilterItemActionPerformed(evt);
            }
        });
        filterMenu.add(gaussianFilterItem);

        negativeItem.setText("Negative");
        negativeItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                negativeItemActionPerformed(evt);
            }
        });
        filterMenu.add(negativeItem);

        sharpenerMenuItem.setText("Sharpen");
        sharpenerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sharpenerMenuItemActionPerformed(evt);
            }
        });
        filterMenu.add(sharpenerMenuItem);

        laplaceMenuItem.setText("Laplace Filter");
        laplaceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                laplaceMenuItemActionPerformed(evt);
            }
        });
        filterMenu.add(laplaceMenuItem);

        menuBar.add(filterMenu);

        viewMenu.setText("View");

        switchListViewItem.setText("List of file names");
        switchListViewItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                switchListViewItemActionPerformed(evt);
            }
        });
        viewMenu.add(switchListViewItem);
        viewMenu.add(jSeparator1);

        silversteinCMItem.setSelected(true);
        silversteinCMItem.setText("Silverstein Color Map");
        silversteinCMItem.setName(""); // NOI18N
        silversteinCMItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                silversteinCMItemActionPerformed(evt);
            }
        });
        viewMenu.add(silversteinCMItem);

        jstarCMItem.setText("Custom Color Map");
        jstarCMItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jstarCMItemActionPerformed(evt);
            }
        });
        viewMenu.add(jstarCMItem);

        grayscaleCMItem.setText("grayscale Color Map");
        grayscaleCMItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                grayscaleCMItemActionPerformed(evt);
            }
        });
        viewMenu.add(grayscaleCMItem);

        rtgCMItem.setText("RTG grayscale");
        rtgCMItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rtgCMItemActionPerformed(evt);
            }
        });
        viewMenu.add(rtgCMItem);

        menuBar.add(viewMenu);

        setJMenuBar(menuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void openFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openFileMenuItemActionPerformed
        JFileChooser chooser = new JFileChooser(new File(initialPath));
        //FileNameExtensionFilter filter = new FileNameExtensionFilter("DCOM Images", "dcm");
        //chooser.setFileFilter(filter);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
            initialPath = file.getPath();
            try {
                DicomFileContent fc = DicomTools.openDicomFile(file, getActiveMapper());

                if (fc == null) {
                    status.setText("invalid file!");
                    return;
                }

                int pos = -1;
                for (int i = 0; i < fileList.getModel().getSize(); i++) {
                    if (fc.equals(fileList.getModel().getElementAt(i))) {
                        pos = i;
                        break;
                    }
                }
                if (pos == -1) {
                    ((DefaultListModel) fileList.getModel()).addElement(fc);
                    pos = fileList.getModel().getSize() - 1;
                }
                currentImg = pos;
                fileList.setSelectedIndex(currentImg);
                fileList.repaint();
                iManager.updateImg(fileList.getModel().getElementAt(currentImg).getImage());
                iManager.repaint(zoomer.getCurrentScale());
                patientData.setText(fileList.getModel().getElementAt(currentImg).getData());
                updateStatus();
                System.out.println("nazwa wybranego pliku" + file.getName());
            } catch (Exception ex) {
                Logger.getLogger(DicomExplorer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_openFileMenuItemActionPerformed

    private void savePNGMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savePNGMenuItemActionPerformed
        JFileChooser chooser = new JFileChooser(new File(initialPath));
        chooser.setFileFilter(new FileNameExtensionFilter(".png", "png"));
        int result = chooser.showSaveDialog(null);
        if (currentImg >= 0) {//sprawdza czy cos jest w 
            //System.out.println("jest obraz");
            if (result == JFileChooser.APPROVE_OPTION) {
                ImageIcon icon = (ImageIcon) imageHolder.getIcon();
                BufferedImage obrazek = (BufferedImage) ((Image) icon.getImage());
                File saveFile = chooser.getSelectedFile();
                if (FilenameUtils.getExtension(saveFile.getName()).equalsIgnoreCase(".png")) {
                } else {
                    saveFile = new File(saveFile.toString() + ".png");
                    saveFile = new File(saveFile.getParentFile(), FilenameUtils.getBaseName(saveFile.getName()) + ".png"); // ALTERNATIVELY: remove the extension (if any) and replace it with ".xml"
                }
                try {
                    ImageIO.write(obrazek, "png", saveFile);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                System.out.println("empty");
            }
        }
    }//GEN-LAST:event_savePNGMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        ask4ExitConfirmation();
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void ask4ExitConfirmation() {
        if (currentImg == -1) {
            this.serializeState();
            this.dispose();
            System.exit(0);
        }

        int opt = JOptionPane.showConfirmDialog(this, "Really exit?", "Exit requested", JOptionPane.YES_NO_OPTION);

        if (opt == 0) {
            this.serializeState();
            this.dispose();
            System.exit(0);
        }
    }

    private void serializeState() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(rcPath))) {
            out.writeObject(initialPath);
            out.writeObject(getActiveMapper().getClass().getCanonicalName());
            DefaultListModel<DicomFileContent> m = (DefaultListModel<DicomFileContent>) fileList.getModel();
            int n = 0;
            for (int i = 0; i < m.getSize(); i++) {  // to jest brzydka łata
                if (m.elementAt(i) != null) {
                    n++;
                }
            }
            out.writeObject(n);
            for (int i = 0; i < m.getSize(); i++) {
                if (m.elementAt(i) != null) {
                    m.elementAt(i).writeExternal(out);
                }
            }
            out.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    private void deSerializeState() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(rcPath))) {
            initialPath = (String) in.readObject();
            String mapperClass = (String) in.readObject();
            selectMapperByClassName(mapperClass);
            PixelDataMapper mpr = getActiveMapper();
            int n = (Integer) in.readObject();
            DefaultListModel<DicomFileContent> model = new DefaultListModel<>();
            for (int i = 0; i < n; i++) {
                DicomFileContent c = new DicomFileContent(in);
                c.updateImage(mpr);
                model.addElement(c);
            }
            fileList.setModel(model);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            initialPath = "";
        }
    }

    private void openDirMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openDirMenuItemActionPerformed

        JFileChooser chooser = new JFileChooser(new File(initialPath));
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] list = chooser.getSelectedFiles();
            file = list[0];
            initialPath = file.getParent();
            if (list.length > 1) {
                try {
                    DicomTools.processFileList(list, (DefaultListModel<DicomFileContent>) fileList.getModel(), getActiveMapper());
                } catch (Exception ex) {
                    Logger.getLogger(DicomExplorer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                File dir = file.isFile() ? file.getParentFile() : file;
                try {
                    DicomTools.readDicomDir(dir, (DefaultListModel<DicomFileContent>) fileList.getModel(), getActiveMapper());
                } catch (Exception ex) {
                    Logger.getLogger(DicomExplorer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            currentImg = 0;
            fileList.setSelectedIndex(0);
            fileList.repaint();
            iManager.updateImg(fileList.getModel().getElementAt(currentImg).getImage());
            iManager.repaint(zoomer.getCurrentScale());
            patientData.setText(fileList.getModel().getElementAt(currentImg).getData());
            updateStatus();
        }
    }//GEN-LAST:event_openDirMenuItemActionPerformed

    private void brightnessSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_brightnessSliderStateChanged
        if (currentImg >= 0) {
            if (brightness != null) {
                iManager.rmFilter(brightness);
            }
            int brightnessValue = brightnessSlider.getValue();
            int contrastValue = contrastSlider.getValue();
            if (brightnessValue != NORMAL_4_BRIGHTNESS_SLIDER || contrastValue != NORMAL_4_CONTRAST_SLIDER) {
                float b = (float) (brightnessValue / (float) brightnessSlider.getMaximum());
                float c = (float) (contrastValue / (float) contrastSlider.getMaximum());
                //System.out.println("b=" + (brightnessValue / (float) brightnessSlider.getMaximum()));
                // legacy brightness = new BrightnessEnhancer(brightnessValue / (float)NORMAL_4_BRIGHTNESS_AND_CONTRAST_SLIDER, 0.0f);
                brightness = new ContrastFilter(b, c);
                iManager.addFilter(brightness);
            }
            iManager.repaint(zoomer.getCurrentScale());
            updateStatus();
        }
    }//GEN-LAST:event_brightnessSliderStateChanged

    private void switchListViewItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_switchListViewItemActionPerformed
        if (switchListViewItem.getText().equals("List of file names")) {
            switchListViewItem.setText("File content miniatures");
        } else {
            switchListViewItem.setText("List of file names");
        }
        listRenderer.switchView();
        fileList.repaint();
    }//GEN-LAST:event_switchListViewItemActionPerformed

    private void zoomInitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInitActionPerformed
        zoomSlider.setValue(NORMAL_4_ZOOM_SLIDER);
        zoomer.stateChanged(new ChangeEvent(zoomSlider));
    }//GEN-LAST:event_zoomInitActionPerformed

    private BufferedImageOp switchFilter(BufferedImageOp filter, JMenuItem filterItem, Class filterClass) {
        // ostatni argument jest potrzebny, bo gdy filter == null  , trzeba wtedy zrobić obiekt dobrego typu
        final String disableStr = "Disable ";
        if (filter == null) {
            try {
                filter = (BufferedImageOp) filterClass.newInstance();
            } catch (Exception e) { // should never happen
                e.printStackTrace();
            }
            iManager.addFilter(filter);
            iManager.repaint(zoomer.getCurrentScale());
            filterItem.setText(disableStr + filterItem.getText());
        } else {
            iManager.rmFilter(filter);
            iManager.repaint(zoomer.getCurrentScale());
            filter = null;
            filterItem.setText(filterItem.getText().replace(disableStr, ""));
        }
        updateStatus();
        return filter;
    }

    private void histEqualizationItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_histEqualizationItemActionPerformed
        equalizer = switchFilter(equalizer, histEqualizationItem, HistogramEqualizationFilter.class);
    }//GEN-LAST:event_histEqualizationItemActionPerformed

    private void gaussianFilterItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gaussianFilterItemActionPerformed
        //gaussian = switchFilter(gaussian, gaussianFilterItem, GaussianFilter.class );
        gaussian = switchFilter(gaussian, gaussianFilterItem, GaussianByConvolve.class);
    }//GEN-LAST:event_gaussianFilterItemActionPerformed

    private void negativeItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_negativeItemActionPerformed
        negative = switchFilter(negative, negativeItem, Negative.class);
    }//GEN-LAST:event_negativeItemActionPerformed

    private void silversteinCMItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_silversteinCMItemActionPerformed
        jRadioButtonMenuItemAction((JRadioButtonMenuItem) evt.getSource());
    }//GEN-LAST:event_silversteinCMItemActionPerformed

    private void jstarCMItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jstarCMItemActionPerformed
        jRadioButtonMenuItemAction((JRadioButtonMenuItem) evt.getSource());
    }//GEN-LAST:event_jstarCMItemActionPerformed

    private void grayscaleCMItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_grayscaleCMItemActionPerformed
        jRadioButtonMenuItemAction((JRadioButtonMenuItem) evt.getSource());
    }//GEN-LAST:event_grayscaleCMItemActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        ask4ExitConfirmation();
    }//GEN-LAST:event_formWindowClosing

    private void clearFilesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearFilesButtonActionPerformed
        ((DefaultListModel<DicomFileContent>) fileList.getModel()).removeAllElements();
    }//GEN-LAST:event_clearFilesButtonActionPerformed

    private void sortFilesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortFilesButtonActionPerformed
        DefaultListModel<DicomFileContent> m = (DefaultListModel<DicomFileContent>) fileList.getModel();
        if (m.getSize() < 2) {
            return;
        }
        Object[] list = m.toArray();
        Arrays.sort(list);
        m.removeAllElements();
        for (Object c : list) {
            m.addElement((DicomFileContent) c);
        }
        currentImg = 0;
        fileList.setSelectedIndex(0);
        fileList.repaint();
    }//GEN-LAST:event_sortFilesButtonActionPerformed

    private void sharpenerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sharpenerMenuItemActionPerformed
        sharpen = switchFilter(sharpen, sharpenerMenuItem, Sharpener.class);
    }//GEN-LAST:event_sharpenerMenuItemActionPerformed

    private void laplaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_laplaceMenuItemActionPerformed
        laplace = switchFilter(laplace, laplaceMenuItem, Laplace.class);
    }//GEN-LAST:event_laplaceMenuItemActionPerformed

    private void contrastSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_contrastSliderStateChanged
        brightnessSliderStateChanged(evt);
    }//GEN-LAST:event_contrastSliderStateChanged

    private void rtgCMItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rtgCMItemActionPerformed
        jRadioButtonMenuItemAction((JRadioButtonMenuItem) evt.getSource());
    }//GEN-LAST:event_rtgCMItemActionPerformed

    private void jRadioButtonMenuItemAction(JRadioButtonMenuItem src) {
        for (JRadioButtonMenuItem i : colorMappers.keySet()) {
            i.setSelected(false);
            i.setEnabled(true);
        }
        src.setSelected(true);
        src.setEnabled(false);
        for (int i = 0; i < fileList.getModel().getSize(); i++) {
            fileList.getModel().getElementAt(i).updateImage(colorMappers.get(src));
        }
        fileList.repaint();
        if (currentImg != -1) {
            iManager.updateImg(fileList.getModel().getElementAt(currentImg).getImage());
        }
        iManager.repaint(zoomer.getCurrentScale());
        updateStatus();
    }

    private PixelDataMapper getActiveMapper() {
        for (JRadioButtonMenuItem b : colorMappers.keySet()) {
            if (b.isSelected()) {
                return colorMappers.get(b);
            }
        }
        throw new NullPointerException("DicomExplorer::getActiveMapper : any mapper must be selected!"); // should never happen
    }

    private void selectMapperByClassName(String name) {
        for (JRadioButtonMenuItem b : colorMappers.keySet()) {
            if (colorMappers.get(b).getClass().getCanonicalName().equals(name)) {
                b.setSelected(true);
                b.setEnabled(false);
            } else {
                b.setSelected(false);
                b.setEnabled(true);
            }
        }
    }

    private void updateStatus() {
        if (currentImg >= 0 && currentImg < fileList.getModel().getSize()) {
            StringBuilder sb = new StringBuilder("HU: " + fileList.getModel().getElementAt(currentImg).getHURange() + " Applied filters: ");
            if (iManager.size() == 0) {
                sb.append(" none");
            }
            for (BufferedImageOp f : iManager) {
                sb.append(f.toString()).append(' ');
            }
            status.setText(sb.toString());
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(DicomExplorer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(DicomExplorer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(DicomExplorer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(DicomExplorer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new DicomExplorer().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSlider brightnessSlider;
    private javax.swing.JButton clearFilesButton;
    private javax.swing.JPanel contrastPanel;
    private javax.swing.JSlider contrastSlider;
    private javax.swing.JPanel controlPanel;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JList<DicomFileContent> fileList;
    private javax.swing.JScrollPane fileListScroll;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JPanel filePanel;
    private javax.swing.JLabel filesTitle;
    private javax.swing.JMenu filterMenu;
    private javax.swing.JMenuItem gaussianFilterItem;
    private javax.swing.JRadioButtonMenuItem grayscaleCMItem;
    private javax.swing.JMenuItem histEqualizationItem;
    private javax.swing.JLabel imageHolder;
    private javax.swing.JScrollPane imgScroll;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JRadioButtonMenuItem jstarCMItem;
    private javax.swing.JMenuItem laplaceMenuItem;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem negativeItem;
    private javax.swing.JMenuItem openDirMenuItem;
    private javax.swing.JMenuItem openFileMenuItem;
    private javax.swing.JLabel patientData;
    private javax.swing.JLabel pixelData;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JRadioButtonMenuItem rtgCMItem;
    private javax.swing.JMenuItem savePNGMenuItem;
    private javax.swing.JPopupMenu.Separator separator1;
    private javax.swing.JPopupMenu.Separator separator2;
    private javax.swing.JMenuItem sharpenerMenuItem;
    private javax.swing.JRadioButtonMenuItem silversteinCMItem;
    private javax.swing.JButton sortFilesButton;
    private javax.swing.JLabel status;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JMenuItem switchListViewItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JButton zoomInit;
    private javax.swing.JPanel zoomPanel;
    private javax.swing.JSlider zoomSlider;
    // End of variables declaration//GEN-END:variables

}
