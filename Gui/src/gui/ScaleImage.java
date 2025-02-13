/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 * Zwiekszanie liczby pixeli poprzez interpolacje
 *
 * @author Małgorzata
 */
public class ScaleImage {

    int w;
    int h;

    public BufferedImage makeImage(BufferedImage imageInput, double height, double width) { //,Color background)   

        this.h = (int) (imageInput.getHeight() * height);
        this.w = (int) (imageInput.getWidth() * width);

        BufferedImage newImage = new BufferedImage(w, h, imageInput.getType());
        Graphics2D g = newImage.createGraphics();
        try {
            g.clearRect(0, 0, w, h);
            g.drawImage(imageInput, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return newImage;
    }

    public BufferedImage getPicture(JLabel jLabel_zmienna) {
        Icon ic = jLabel_zmienna.getIcon();
        ImageIcon icon = (ImageIcon) ic;
        BufferedImage obrazek = (BufferedImage) ((Image) icon.getImage());
//        System.out.println("obraz z getPicture");   
        return obrazek;
    }

    public static ArrayList<int[]> LUT(BufferedImage iInput) {
        ArrayList<int[]> LUTi = new ArrayList<>();
        int pixel = iInput.getWidth() * iInput.getHeight();
        int[] rParameter = new int[pixel];
        int[] gParameter = new int[pixel];
        int[] bParameter = new int[pixel];
        Arrays.fill(rParameter, 0);
        Arrays.fill(gParameter, 0);
        Arrays.fill(bParameter, 0);

        int k = 0;
        int rVal;
        int gVal;
        int bVal;
        for (int i = 0; i < iInput.getWidth(); i++) {
            for (int j = 0; j < iInput.getHeight(); j++) {

                rVal = new Color(iInput.getRGB(i, j)).getRed();
                gVal = new Color(iInput.getRGB(i, j)).getGreen();
                bVal = new Color(iInput.getRGB(i, j)).getBlue();

                rParameter[k] = rVal;
                gParameter[k] = gVal;
                bParameter[k] = bVal;
                k++;
            }
        }
        LUTi.add(rParameter);
        LUTi.add(gParameter);
        LUTi.add(bParameter);

        return LUTi;
    }

    public BufferedImage compareImage(BufferedImage imLabel, BufferedImage imSlider) {
        ArrayList<int[]> LUTLabel = LUT(imLabel);
        ArrayList<int[]> LUTSlider = LUT(imSlider);
        BufferedImage imagNew = null;
//     System.out.println("rozmiar listy "+LUTSlider.size());
        for (int i = 0; i < LUTSlider.size(); i++) {
//         System.out.println("LUTLabel.get(0)[i] "+LUTLabel.get(0)[i]);
            if (LUTLabel.get(0)[i] != LUTSlider.get(0)[i]) {
                imagNew = imLabel;
//             System.out.println("obraz z imLabel");
                break;
            } else {
                imagNew = imSlider;
            }
//         System.out.println("obraz wczesniej wczytany");
        }
        return imagNew;
    }
}
