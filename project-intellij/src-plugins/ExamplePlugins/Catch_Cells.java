import ij.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.MeasurementsWriter;
import ij.plugin.frame.ThresholdAdjuster;
import ij.plugin.ChannelSplitter;
import ij.plugin.Thresholder;

import java.lang.String;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.awt.Polygon;
import java.lang.Object;
import java.util.Arrays;

    /*Catch_Cells by Tatalessap
     *
     * Catch_Cells � una classe volta alla misurazione di elementi all'interno di una immagine (binaria+grey+RGB).
     * Si appoggia al Plugin "ThresholdAdjuster" per poter individuare le zone e gli oggetti da analizzare.
     * Implementa Plugin ma al suo interno vi � una classe Cells_Analyzer che estende a sua volta il PlugInFilter "ParticleAnalyzer"
     * aggiungendo misure non presenti in quest'ultimo.*/
    public class Catch_Cells implements PlugIn {
        ImagePlus impPlusesRGB [];
        ImagePlus impPlusesHSB [];
        ImagePlus imp;
        boolean typeRGB;

        /*Metodo run necessaria per i PlugIn
         * Memorizza l'immagine in ingresso
         * richiama il metodo catch_parasite_running dandogli in ingresso l'immagine*/
        public void run(String arg) {
            imp = IJ.getImage();
            if (imp.getType() == ImagePlus.COLOR_RGB) typeRGB = true;
            if(typeRGB){
                ChannelSplitter ch = new ChannelSplitter();
                impPlusesRGB = ch.split(imp);
                impPlusesHSB = getImagePlusHSB(imp);

            }
            Cells_Analyzer cells_analyzer = new Cells_Analyzer();

            int flags = cells_analyzer.setup("", imp);

            /*Controllo*/
            if (flags == PlugInFilter.DONE)
                return;
            cells_analyzer.run(imp.getProcessor());
            /*visualizzazione dei risultati*/
            Analyzer.getResultsTable().show("Results");

        }

        /*Per poter spillare HSB*/
        private ImagePlus [] getImagePlusHSB(ImagePlus imagePlus){
            ImagePlus [] imagePluses = new ImagePlus[3];
            int w = imagePlus.getWidth();
            int h = imagePlus.getHeight();

            ImageStack hsbStack = imagePlus.getStack();
            ImageStack hueStack = new ImageStack(w,h);
            ImageStack satStack = new ImageStack(w,h);
            ImageStack brightStack = new ImageStack(w,h);

            byte[] hue,s,b;

            ColorProcessor cp;
            int n = hsbStack.getSize();

            for (int i=1; i<=n; i++) {
                hue = new byte[w*h];
                s = new byte[w*h];
                b = new byte[w*h];
                cp = (ColorProcessor)hsbStack.getProcessor(1);
                cp.getHSB(hue,s,b);
                hueStack.addSlice(null,hue);
                satStack.addSlice(null,s);
                brightStack.addSlice(null,b);
            }

            imagePluses[0] = new ImagePlus("hue", hueStack);
            imagePluses[1] = new ImagePlus("sat", satStack);
            imagePluses[2] = new ImagePlus("bright", brightStack);

            return imagePluses;
        }

        /*Inner class Parasite che estende la classe ParticleAnalyzer
         * Al suo interno, attraverso gli Override, si estendono i meotdi gi� presenti nella classe ParticleAnalyzer*/
        class Cells_Analyzer extends ParticleAnalyzer {
            int measure = 0;

            /* Feature di default ImageJ */
            private boolean[] measureImageJ = new boolean[7];

            /* Feature morfologiche implementate ex novo da Marta */
            private boolean[] measuresBW = new boolean[30];
            /*CheckBox Misure aggiunte B&W*/
            private boolean doConvexArea, doConvexPerimeter, doArEquivD,
                    doAspRatio, doPerEquivD, doMINRMAXR,
                    doRoundness, doEquivEllAr, doCompactness,
                    doSolidity, doShape, doRFactor,
                    doConvexity, doConcavity, doArBBox,
                    doRectang, doModRatio, doSphericity,
                    doElongation, doNormPeriIndex, doHaralickRatio,
                    doBendingEnergy,

                    doIS, doDS,
                    doJaggedness, doEndocarp, doBreadth,
                    doMeanRadius, doVarianceR, doCircularity;

            /* Feature texturali implementate ex novo da Marta */
            private boolean[] measuresGrey = new boolean[13];
            /*CheckBox Misure aggiunte GREY*/
            private boolean doMean, doSkewness, doKurtois,
                    doMode, doMedian, doEntropy, doIntensitySum,
                    doSquareIntensitySum, doUniformity, doVariance, doStandardDeviation,
                    doSmothness, doMinandMax;

            /* Feature di colore implementate ex novo da Marta */
            private boolean[] measureRGB = new boolean[16];
            private boolean doMeanRed, doMeanGreen, doMeanBlue,
                            doStdDeviationRed, doStdDeviationGreen, doStdDeviationBlue,
                            doSquareRootMeanR, doSquareRootMeanG, doSquareRootMeanB, doAverageRGBcolors,
                            doMeanHue, doMeanSaturation, doMeanBrightness, doStdDeviationHue, doStdDeviationS,doStdDeviationBr;

            /*piGreco necessario per alcuni calcoli*/
            double pigreco = Math.PI;

            /*showDialog:
             * settaggio della opzione per visualizzare contorni e etichetta numerata
             * settaggio nelle misure necessarie
             * richiamo della genericDialog creata per poter settare le misure da aggiungere*/
            @Override
            public boolean showDialog() {
                //super.staticShowChoice = 1;
                boolean flag = super.showDialog();
                if (flag) {
                    flag = dialogSetMeasurements();
                    setMeasurementsExtended();
                    Analyzer.setMeasurements(measure);
                    return flag;
                }
                return false;
            }

            /*Metodo della creazione della finestra di dialogo, visualizzazione del:
             * - checkbox per selezionare ogni misura
             * - checkbox per selezionare misure singole*/
            private boolean dialogSetMeasurements() {
                GenericDialog gd = new GenericDialog("Measures", IJ.getInstance());
                Font font = new Font("font", Font.ITALIC, 16);
                Font fontSpace = new Font("font", Font.PLAIN, 8);

                gd.addMessage("Select the measures for B&W", font, Color.black);
                String[] labels = new String[7];
                boolean[] states = new boolean[7];
                gd.addCheckbox("Select All", true);
                labels[0] = "Area                  ";
                states[0] = false;
                labels[1] = "Centroid              ";
                states[1] = false;
                labels[2] = "Center of mass        ";
                states[2] = false;
                labels[3] = "Perimeter             ";
                states[3] = false;
                labels[4] = "Bounding rectangle    ";
                states[4] = false;
                labels[5] = "Fit ellipse           ";
                states[5] = false;
                labels[6] = "Feret's diameter      ";
                states[6] = false;
                //labels[7] = "Shape descriptors     ";
                //states[7] = false;

                gd.setInsets(1, 0, 0);
                gd.addCheckboxGroup(2, 4, labels, states);
                gd.addMessage(" ", fontSpace, Color.black);

                String[] labels1 = new String[12];
                boolean[] states1 = new boolean[12];
                labels1[0] = "Convex Area*           ";
                states1[0] = false;
                labels1[1] = "Convex Perimeter*     ";
                states1[1] = false;
                labels1[2] = "ArEquivD*             ";
                states1[2] = false;
                labels1[3] = "AspRatio*              ";
                states1[3] = false;
                labels1[4] = "PerEquivD*            ";
                states1[4] = false;
                labels1[5] = "MinR and MaxR*        ";
                states1[5] = false;
                labels1[6] = "Roundness*            ";
                states1[6] = false;
                labels1[7] = "EquivEllAr*           ";
                states1[7] = false;
                labels1[8] = "Compactness*          ";
                states1[8] = false;
                labels1[9] = "Solidity*             ";
                states1[9] = false;
                labels1[10] = "ThinnessR*               ";
                states1[10] = false;
                labels1[11] = "RFactor*                ";
                states1[11] = false;

                gd.setInsets(0, 0, 0);
                gd.addCheckboxGroup(3, 4, labels1, states1);
                gd.addMessage(" ", fontSpace, Color.black);

                String[] labels2 = new String[12];
                boolean[] states2 = new boolean[12];
                labels2[0] = "Convexity*             ";
                states2[0] = false;
                labels2[1] = "Concavity*              ";
                states2[1] = false;
                labels2[2] = "ArBBox*                ";
                states2[2] = false;
                labels2[3] = "Rectang*               ";
                states2[3] = false;
                labels2[4] = "ModRatio*              ";
                states2[4] = false;
                labels2[5] = "Sphericity*            ";
                states2[5] = false;
                labels2[6] = "Elongation*            ";
                states2[6] = false;
                labels2[7] = "NormPeriIndex*         ";
                states2[7] = false;
                labels2[8] = "HaralickRatio*         ";
                states2[8] = false;
                labels2[9] = "Bending Energy*        ";
                states2[9] = false;
                labels2[10] = "IS**        ";
                states2[10] = false;
                labels2[11] = "DS**        ";
                states2[11] = false;

                gd.setInsets(0, 0, 0);
                gd.addCheckboxGroup(3, 4, labels2, states2);
                gd.addMessage(" ", fontSpace, Color.black);

                String[] labels5 = new String[6];
                boolean[] states5 = new boolean[6];
                labels5[0] = "Jaggedness**       ";
                states5[0] = false;
                labels5[1] = "Endocarp**              ";
                states5[1] = false;
                labels5[2] = "Breadth**               ";
                states5[2] = false;
                labels5[3] = "MeanRadius**            ";
                states5[3] = false;
                labels5[4] = "VarianceRadius**        ";
                states5[4] = false;
                labels5[5] = "Circularity**           ";
                states5[5] = false;

                gd.setInsets(0, 0, 0);
                gd.addCheckboxGroup(2, 4, labels5, states5);
                gd.addMessage(" ", fontSpace, Color.black);

                gd.addMessage("Select the measures for Grey", font, Color.GRAY);

                if(typeRGB){
                    String[] labels3 = new String[12];
                    boolean[] states3 = new boolean[12];
                    gd.addCheckbox("Select All", true);
                    labels3[0] = "Mean                      ";
                    states3[0] = false;
                    labels3[1] = "Skewness                  ";
                    states3[1] = false;
                    labels3[2] = "Intensity Sum*            ";
                    states3[2] = false;
                    labels3[3] = "Mode                      ";
                    states3[3] = false;
                    labels3[4] = "Kurtosis                  ";
                    states3[4] = false;
                    labels3[5] = "Entropy*                  ";
                    states3[5] = false;
                    labels3[6] = "Uniformity*               ";
                    states3[6] = false;
                    labels3[7] = "SqI sum*                  ";
                    states3[7] = false;
                    labels3[8] = "Std deviation            ";
                    states3[8] = false;
                    labels3[9] = "Variance*                ";
                    states3[9] = false;
                    labels3[10] = "Median                   ";
                    states3[10] = false;
                    labels3[11] = "Smoothness R*            ";
                    states3[11] = false;
                    gd.setInsets(0, 0, 0);
                    gd.addCheckboxGroup(3, 4, labels3, states3);
                }else
                {
                    String[] labels3 = new String[13];
                    boolean[] states3 = new boolean[13];
                    gd.addCheckbox("Select All", true);
                    labels3[0] = "Mean                      ";
                    states3[0] = false;
                    labels3[1] = "Skewness                  ";
                    states3[1] = false;
                    labels3[2] = "Intensity Sum*            ";
                    states3[2] = false;
                    labels3[3] = "Mode                      ";
                    states3[3] = false;
                    labels3[4] = "Kurtosis                  ";
                    states3[4] = false;
                    labels3[5] = "Entropy*                  ";
                    states3[5] = false;
                    labels3[6] = "Uniformity*               ";
                    states3[6] = false;
                    labels3[7] = "SqI sum*                  ";
                    states3[7] = false;
                    labels3[8] = "Std deviation             ";
                    states3[8] = false;
                    labels3[9] = "Variance*                 ";
                    states3[9] = false;
                    labels3[10] = "Median                   ";
                    states3[10] = false;
                    labels3[11] = "Smoothness R*            ";
                    states3[11] = false;
                    labels3[12] = "Min and Max              ";
                    states3[12] = false;
                    gd.setInsets(0, 0, 0);
                    gd.addCheckboxGroup(3, 4, labels3, states3);
                }

                if(typeRGB){
                    gd.addMessage("Select the measures for RGB", font, Color.red);
                   // gd.addMessage("Select the measures", font, colors[0]+ "for RGB implemented", font, colors[1]);
                    gd.addCheckbox("Select All", true);
                    String[] labels4 = new String[16];
                    boolean[] states4 = new boolean[16];
                    labels4[0] = "R mean*                      ";
                    states4[0] = false;
                    labels4[1] = "R std deviation*             ";
                    states4[1] = false;
                    labels4[2] = "Square root of mean R*       ";
                    states4[2] = false;
                    labels4[3] = "G mean*                      ";
                    states4[3] = false;
                    labels4[4] = "G std deviation*             ";
                    states4[4] = false;
                    labels4[5] = "Square root of mean G*       ";
                    states4[5] = false;
                    labels4[6] = "B mean*                      ";
                    states4[6] = false;
                    labels4[7] = "B std deviation*             ";
                    states4[7] = false;
                    labels4[8] = "Square root of mean B*       ";
                    states4[8] = false;
                    labels4[9] = "Average RGB colors*          ";
                    states4[9] = false;
                    labels4[10] = "H mean*                     ";
                    states4[10] = false;
                    labels4[11] = "H std deviation*            ";
                    states4[11] = false;
                    labels4[12] = "S mean*                     ";
                    states4[12] = false;
                    labels4[13] = "S std deviation*            ";
                    states4[13] = false;
                    labels4[14] = "Br mean*                    ";
                    states4[14] = false;
                    labels4[15] = "Br std deviation*           ";
                    states4[15] = false;

                    gd.setInsets(0, 0, 0);
                    gd.addCheckboxGroup(6, 3, labels4, states4);
                }

                gd.showDialog(); //show
                if (gd.wasCanceled())
                    return false;

                boolean spam = false;

                //imageJ
                if (gd.getNextBoolean()) {
                    for (int i = 0; i < measureImageJ.length; i++) {
                        measureImageJ[i] = true;
                        spam = gd.getNextBoolean();
                    }
                    for (int i = 0; i < measuresBW.length; i++) {
                        measuresBW[i] = true;
                        spam = gd.getNextBoolean();
                    }
                } else {
                    for (int i = 0; i < measureImageJ.length; i++) {
                        measureImageJ[i] = gd.getNextBoolean();
                    }
                    for (int i = 0; i < measuresBW.length; i++) {
                        measuresBW[i] = gd.getNextBoolean();
                    }
                }

                //grey
                if (gd.getNextBoolean()) {
                    if (typeRGB) {
                        for (int i = 0; i < measuresGrey.length - 1; i++) {
                            measuresGrey[i] = true;
                            spam = gd.getNextBoolean();
                        }
                    } else {
                        for (int i = 0; i < measuresGrey.length; i++) {
                            measuresGrey[i] = true;
                            //spam = gd.getNextBoolean();
                        }
                    }
                }else{
                    if(typeRGB){
                        for (int i = 0; i < measuresGrey.length-1; i++) {
                            measuresGrey[i] = gd.getNextBoolean();
                        }

                    }else{
                        for (int i = 0; i < measuresGrey.length-1; i++) {
                            measuresGrey[i] = gd.getNextBoolean();
                        }
                    }
                }

                if(typeRGB){
                    if(gd.getNextBoolean()){
                        for (int i = 0; i < measureRGB.length; i++) {
                            measureRGB[i] = true;
                            spam = gd.getNextBoolean();
                        }
                    } else {
                        for (int i = 0; i < measureRGB.length; i++) {
                            measureRGB[i] = gd.getNextBoolean();
                        }
                    }
                }
                return true;
            }

            /**/
            private void setMeasurementsExtended() {
                /*Misure by ImageJ*/
                if (measureImageJ[0]) {
                    measure += AREA;
                }
                if (measureImageJ[1]) {
                    measure += CENTROID;
                }
                if (measureImageJ[2]) {
                    measure += CENTER_OF_MASS;
                }
                if (measureImageJ[3]) {
                    measure += PERIMETER;
                }
                if (measureImageJ[4]) {
                    measure += RECT;
                }
                if (measureImageJ[5]) {
                    measure += ELLIPSE;
                }
                if (measureImageJ[6]) {
                    measure += FERET;
                }
                /*
                if (measureImageJ[7]) {
                    measure += SHAPE_DESCRIPTORS;
                }
                */

                /*Misure BW*/
                doConvexArea = measuresBW[0];
                doConvexPerimeter = measuresBW[1];
                doArEquivD = measuresBW[2];
                doAspRatio = measuresBW[3];
                doPerEquivD = measuresBW[4];
                doMINRMAXR = measuresBW[5];
                doRoundness = measuresBW[6];
                doEquivEllAr = measuresBW[7];
                doCompactness = measuresBW[8];
                doSolidity = measuresBW[9];
                doShape = measuresBW[10];
                doRFactor = measuresBW[11];
                doConvexity = measuresBW[12];
                doConcavity = measuresBW[13];
                doArBBox = measuresBW[14];
                doRectang = measuresBW[15];
                doModRatio = measuresBW[16];
                doSphericity = measuresBW[17];
                doElongation = measuresBW[18];
                doNormPeriIndex = measuresBW[19];
                doHaralickRatio = measuresBW[20];
                doBendingEnergy = measuresBW[21];

                // new features, specific for seeds analysis
                doIS = measuresBW[22];
                doDS = measuresBW[23];
                doJaggedness = measuresBW[24];
                doEndocarp = measuresBW[25];
                doBreadth = measuresBW[26];
                doMeanRadius = measuresBW[27];
                doVarianceR = measuresBW[28];
                doCircularity = measuresBW[29];

                doMean = measuresGrey[0];
                doSkewness = measuresGrey[1]; if (doSkewness) { measure += SKEWNESS; }
                doIntensitySum = measuresGrey[2];
                doMode = measuresGrey[3];
                doKurtois = measuresGrey[4]; if (doKurtois) { measure += KURTOSIS; }
                doEntropy = measuresGrey[5];
                doUniformity = measuresGrey[6];
                doSquareIntensitySum = measuresGrey[7];
                doStandardDeviation = measuresGrey[8];
                doVariance = measuresGrey[9];
                doMedian = measuresGrey[10]; if (doMedian) { measure += MEDIAN; }
                doSmothness = measuresGrey[11];
                if(!typeRGB) doMinandMax= measuresGrey[12]; if(doMinandMax) { measure += MIN_MAX;}

                doMeanRed = measureRGB[0];
                doStdDeviationRed = measureRGB[1];
                doSquareRootMeanR = measureRGB[2];
                doMeanGreen = measureRGB[3];
                doStdDeviationGreen = measureRGB[4];
                doSquareRootMeanG = measureRGB[5];
                doMeanBlue = measureRGB[6];
                doStdDeviationBlue = measureRGB[7];
                doSquareRootMeanB = measureRGB[8];
                doAverageRGBcolors = measureRGB[9];
                doMeanHue= measureRGB[10];
                doStdDeviationHue= measureRGB[11];
                doMeanSaturation= measureRGB[12];
                doStdDeviationS= measureRGB[13];
                doMeanBrightness= measureRGB[14];
                doStdDeviationBr= measureRGB[15];
            }

            /*saveResults:
             *richiamo della funzione originale e aggiunta delle nuove misure*/
            protected void saveResults(ImageStatistics stats, Roi roi) {
                super.saveResults(stats, roi);

                /*Settaggio delle misure da aggiungere date dalla checkBox*/
                /*Oggetti e dati necessari per alcune misure*/
                Polygon polygon = roi.getConvexHull();
                double convexArea = getArea(polygon);
                double convexPerimeter = getPerimeter(polygon);
                double[] feret = roi.getFeretValues(); //0 --> feret , 1 --> angle, 2 --> feret min
                double perim = roi.getLength();
                int[] hist = stats.histogram;
                double[] radiiValues = getRadiiValues(roi, stats.xCenterOfMass, stats.yCenterOfMass);

                if (doConvexArea) { //Area of the convex hull polygon
                    rt.addValue("*ConvexArea", convexArea);
                }

                if (doConvexPerimeter) { //Perimeter of the convex hull polygon
                    rt.addValue("*ConvexPerimeter", convexPerimeter);
                }

                if (doArEquivD) //Diameter of a circle with equivalent area,
                    rt.addValue("*ArEquivD", Math.sqrt((4 * pigreco) * stats.area));

                if (doAspRatio) //Aspect ratio = Feret/Breadth = L/W also called Feret ratio or Eccentricity or Rectangular ratio
                    rt.addValue("*AspRatio", feret[0] / feret[2]);

                if (doPerEquivD) //Diameter of a circle with equivalent perimeter,  Area/?
                    rt.addValue("*PerEquivD", stats.area / pigreco);

                if (doMINRMAXR) { //
                    rt.addValue("*MinR", feret[2] / 2); //DA RIVEDERE Radius of the inscribed circle centred at the middle of mass
                    rt.addValue("*MaxR", feret[0] / 2); //DA RIVEDERE Radius of the enclosing circle centred at the middle of mass
                }

                if (doRoundness) //Roundness = 4�Area/(?�Feret2)
                    rt.addValue("*Roundness", (stats.area * 4) / ((pigreco) * (feret[0] * feret[0])));

                if (doEquivEllAr) //Area of the ellipse with Feret and Breath as major and minor axis,  = (?�Feret�Breadth)/4
                    rt.addValue("*EquivEllAr", (pigreco * feret[0] * feret[2]) / 4);

                if (doCompactness) //Compactness = sqrt((4/pi)*Area)/Feret
                    rt.addValue("*Compactness", (Math.sqrt((4 / pigreco) * stats.area)) / feret[0]);

                if (doSolidity) //Solidity = Area/ConvexArea
                    rt.addValue("*Solidity", (stats.area / convexArea));

                if (doShape) //Shape = Perimeter2/Area also called Thinness ratio
                    rt.addValue("*ThinnessR", (perim * perim) / stats.area);

                if (doRFactor) //RFactor = Convex_Area /(Feret�?)
                    rt.addValue("*RFactor", convexArea / (feret[0] * pigreco));

                // End of "Labels1". Start of "Labels2"
                if (doConvexity) //Convexity = Convex_Perim/Perimeter also called rugosity or roughness
                    rt.addValue("*Convexity", convexPerimeter / perim);

                if (doConcavity) //Concavity ConvexArea-Area
                    rt.addValue("*Concavity", convexArea - stats.area);

                if (doArBBox) //Area of the bounding box along the Feret diameter = Feret�Breadth
                    rt.addValue("*ArBBox", feret[0] * feret[2]);

                if (doRectang) //Rectangularity = Area/ArBBox also called Extent
                    rt.addValue("*Rectang", stats.area / (feret[0] * feret[2]));

                if (doModRatio) //Modification ratio = (2�MinR)/Feret
                    rt.addValue("*ModRatio", (feret[2] / feret[0])); //2 * MinR / Feret DA RIVEDERE dannorisultati uguali

                if (doSphericity) //Sphericity = MinR/MaxR also called Radius ratio
                    rt.addValue("*Sphericity", (feret[2] / 2) / (feret[0] / 2)); //MinR / MaxR DA RIVEDERE danno risultati ugualu

                if (doElongation) //The inverse of the circularity,  Perim2/(4�?�Area)
                    rt.addValue("*Elongation", (perim * perim) / (4 * pigreco * stats.area));

                if (doNormPeriIndex)
                    rt.addValue("*normPeriIndex", (2 * Math.sqrt(pigreco * stats.area)) / perim);

                if (doHaralickRatio) {
                    double haralickRatio = getHaralickRatio(polygon);
                    rt.addValue("*HaralickRatio", haralickRatio);
                }
                if (doBendingEnergy) {
                    double be = getBendingEnergy(polygon);
                    rt.addValue("*Bending Energy", be);
                }
                if (doIS) {
                    int[] intersectionIS = getIS(roi);
                    rt.addValue("IS: x", intersectionIS[0]);
                    rt.addValue("IS: y", intersectionIS[1]);
                }
                if (doDS) {
                    int[] intersectionIS = getIS(roi);
                    rt.addValue("DS",getDS(roi, intersectionIS, stats.xCenterOfMass, stats.yCenterOfMass));
                }
                if (doJaggedness) {
                    rt.addValue("*Jaggedness", (2*Math.sqrt(Math.PI*stats.area))/perim);
                }
                if (doEndocarp) {
                    rt.addValue("*Endocarp", stats.area - perim);
                }
                if (doBreadth) {
                    rt.addValue("*Breadth", feret[2]);
                }
                if (doMeanRadius) {
                    rt.addValue("*MeanR", radiiValues[2]);
                }
                if (doVarianceR) {
                    rt.addValue("*VarianceR", radiiValues[3]);
                }
                if (doCircularity) {  // 4pi*area/perimeter^2
                    rt.addValue("*Circularity", (4*Math.PI*stats.area)/Math.pow(perim,2));
                }

                /*Grey*/
                if (doVariance) {
                    rt.addValue("**Variance", Math.pow(stats.stdDev, 2));
                }

                if (doStandardDeviation) {
                    rt.addValue("**Standard deviation", stats.stdDev);
                }

                if (doMean) {
                    rt.addValue("**Mean", stats.mean);
                }

                if (doMode) {
                    rt.addValue("**Mode", stats.dmode);
                }

                if (doEntropy) {
                    double e = getEntropy(hist, stats.area);
                    rt.addValue("**Entropy", e);
                }

                if (doIntensitySum) {
                    int intensitySum = getIntensitySum(hist);
                    rt.addValue("**Intensity sum", intensitySum);
                }

                if (doSquareIntensitySum) {
                    int intensitySum = getIntensitySum(hist);
                    rt.addValue("**Square intensity sum (SqI sum)", Math.sqrt((double) intensitySum));
                }

                if (doUniformity) {
                    rt.addValue("**Uniformity", getUniformity(hist, stats.area));
                }

                if (doSmothness) {
                    rt.addValue("**Smothness R", getSmoothness(Math.pow(stats.stdDev, 2)));
                }

                ///PROVA
                if(typeRGB){
                    impPlusesRGB[0].setRoi(roi); //red
                    impPlusesRGB[1].setRoi(roi); //green
                    impPlusesRGB[2].setRoi(roi); //blue

                    ImageStatistics stats_r = impPlusesRGB[0].getAllStatistics();
                    ImageStatistics stats_g = impPlusesRGB[1].getAllStatistics();
                    ImageStatistics stats_b = impPlusesRGB[2].getAllStatistics();

                    if(doMeanRed) {
                        rt.addValue("***Average red color, R mean", stats_r.mean);
                    }

                    if(doMeanGreen){
                        rt.addValue("***Average green color, G mean", stats_g.mean);
                    }

                    if(doMeanBlue){
                        rt.addValue("***Average blue color, B mean", stats_b.mean);
                    }

                    if(doStdDeviationRed){
                        rt.addValue("***Red color std deviation, R std", stats_r.stdDev);
                    }

                    if(doStdDeviationGreen){
                        rt.addValue("***Green color std deviation, G std", stats_g.stdDev);
                    }

                    if(doStdDeviationBlue){
                        rt.addValue("***Blue color std deviation, B std", stats_b.stdDev);
                    }

                    if(doSquareRootMeanR){
                        rt.addValue("***Square root of mean R", Math.sqrt(stats_r.mean));
                    }
                    if(doSquareRootMeanG){
                        rt.addValue("***Square root of mean G", Math.sqrt(stats_g.mean));
                    }
                    if(doSquareRootMeanB){
                        rt.addValue("***Square root of mean B", Math.sqrt(stats_b.mean));
                    }

                    if(doAverageRGBcolors){
                        rt.addValue("***Average RGB colors", (stats_b.mean+stats_g.mean+stats_b.mean)/3);
                    }

                    impPlusesHSB[0].setRoi(roi); //hue
                    impPlusesHSB[1].setRoi(roi); //saturation
                    impPlusesHSB[2].setRoi(roi); //brightness

                    ImageStatistics stats_hu = impPlusesHSB[0].getAllStatistics();
                    ImageStatistics stats_sa = impPlusesHSB[1].getAllStatistics();
                    ImageStatistics stats_br = impPlusesHSB[2].getAllStatistics();

                    if(doMeanHue)rt.addValue("***Average Hue color, H mean", stats_hu.mean);
                    if(doMeanSaturation)rt.addValue("***Average Saturation color, S mean", stats_sa.mean);
                    if(doMeanBrightness)rt.addValue("***Average Brightness color, B mean", stats_br.mean);

                    if(doStdDeviationHue)rt.addValue("***Hue color std deviation", stats_hu.stdDev);
                    if(doStdDeviationS)rt.addValue("***Saturation color std deviation", stats_sa.stdDev);
                    if(doStdDeviationBr)rt.addValue("***Brightness color std deviation", stats_br.stdDev);


                }
            }

            private double getArea(Polygon p) {
                if (p == null) return Double.NaN;
                double carea = 0.0;

                for (int i = 0; i <= p.npoints - 2; i++) {
                    carea += (p.xpoints[i] * p.ypoints[i+1]) - (p.xpoints[i+1] * p.ypoints[i]);
                }
                return (Math.abs(carea / 2.0));
            }

            private final double getPerimeter(Polygon p) {
                if (p == null) return Double.NaN;
                double cperimeter = 0.0;

                for (int i = 0; i <= p.npoints - 2; i++) {
                    cperimeter += distance(p.xpoints[i+1], p.ypoints[i+1], p.xpoints[i], p.ypoints[i]);
                }

                return cperimeter;
            }


            /*Riguardante ConvexHull-
             * Rivisitazione del metodo getArea da Analyzer(super-super classe) per cui dati i punti del poligono calcola l'area
             * trattandolo come se fosse composto da danti piccoli triangoli*/
            private double getAreaOld(Polygon p) {
                if (p == null) return Double.NaN;
                int carea = 0;
                int iminus1;

                for (int i = 0; i < p.npoints - 1; i++) {
                    iminus1 = i - 1;
                    if (iminus1 < 0) iminus1 = p.npoints - 1;
                    carea += (p.xpoints[i] + p.xpoints[iminus1]) * (p.ypoints[i] - p.ypoints[iminus1]);
                }
                return (Math.abs(carea / 2.0));
            }

            /*Riguardante ConvexHull-
             * Calcolo del perimetro dato i punti del poligono e calcolando le distanze tra i punti*/
            private final double getPerimeterOld(Polygon p) {
                if (p == null) return Double.NaN;

                double cperimeter = 0.0;
                int iminus1;

                for (int i = 0; i < p.npoints - 1; i++) {
                    iminus1 = i - 1;
                    if (iminus1 < 0) iminus1 = p.npoints - 1;
                    cperimeter += distance(p.xpoints[i], p.ypoints[i], p.xpoints[iminus1], p.ypoints[iminus1]);
                }
                return cperimeter;
            }

            private double getBendingEnergy(Polygon polygon) {
                //SBAGLIATO DA RICONTROLLARE
                double bendingEnergy = 0;
                double[] k = new double[polygon.npoints];
                int i;

                /*Trasformazione in double dei vettori x e y..
                 * */
                double[] x = new double[polygon.npoints];
                for (i = 0; i < x.length; i++) {
                    x[i] = (double) polygon.xpoints[i];
                }

                double[] y = new double[polygon.npoints];
                for (i = 0; i < y.length; i++) {
                    y[i] = (double) polygon.ypoints[i];
                }

                k = divVector(
                        diffVector
                                (
                                        moltVector(
                                                (diff(x)), (diff(diff(y)))
                                        )
                                        ,
                                        moltVector(
                                                (diff(y)), (diff(diff(x)))
                                        )
                                )
                        ,
                        elevationVector(
                                (sumVector(
                                        elevationVector(diff(x), 2),
                                        elevationVector(diff(y), 2))),
                                1.5
                        )

                );


                for (i = 0; i < k.length; i++) {
                    bendingEnergy = bendingEnergy + Math.pow(k[i], 2);
                }

                return bendingEnergy * Math.pow(k.length, -1);
            }

            /*Riguardante il calcolo del Haralick Ratio
             * Sfruttando la trasformazione in convexHull del roi, si prendono i punti delle coordinate x e y e si lavorano su essi:
             * ovunque la y abbia un valore uguale si considera la x
             * purtroppo essi sono disordinati*/
            private double getHaralickRatio(Polygon p) {
                /*Essendo disordinati bisogna scorrere gli array delle x e delle y*/
                double sumMean = 0.0;
                /*raccoglier� tutti i raggi*/
                double[] radii = new double[p.npoints];
                int xSecond = 0;
                int numberOfRadius = 0;
                for (int i = 0; i < p.npoints - 1; i++) {
                    for (int j = i + 1; j < p.npoints - 1; j++) {
                        if (p.ypoints[j] == p.ypoints[i]) {
                            if (p.xpoints[i] < p.xpoints[j]) {
                                xSecond = p.xpoints[j];
                            }
                        }
                    }
                    sumMean += (xSecond - p.xpoints[i]) / 2;
                    radii[numberOfRadius] = (xSecond - p.xpoints[i]) / 2;
                    numberOfRadius++;
                }

                double media = sumMean / numberOfRadius;
                for (int i = 0; i < numberOfRadius; i++) {
                    sumMean += Math.abs((radii[i] - media) * (radii[i] - media));
                }
                return media / (Math.sqrt(Math.abs(sumMean / numberOfRadius - 1)));
            }

            private double getEntropy(int[] hist, double area) {
                double sum = 0.0;
                for (int i = 0; i < hist.length; i++) {
                    if (hist[i] != 0) sum += (double) (hist[i] / area) * log2((double) (hist[i] / area));
                }

                return -sum;
            }

            private int getIntensitySum(int[] hist) {
                int sum = 0;
                for (int i = 0; i < hist.length; i++) {
                    sum += i * hist[i];
                }
                return sum;
            }

            private double getUniformity(int[] hist, double area) {
                double uniformity = 0.0;

                for (int i = 0; i < hist.length; i++) {
                    uniformity += Math.pow(((double) hist[i] / area), 2);
                }

                return uniformity;
            }

            private double getSmoothness(double variance) {
                //the variance in this measure is normalized to the rage [0, 1] by dividing it by (L-1)^2
                //L --> grey level

                //only grey level in the object or in the histagram?
                int greyLevel = 256;

                double varianceNormalized = variance / Math.pow(greyLevel - 1, 2);

                return 1 - (1 / (1 + varianceNormalized));
            }

            private double getEndocarp(double area, double perimeter) {

                return area - perimeter;

            }


            /*Funzione di appoggio per il caolcolo della distanza*/
            public double distance(int argx1, int argy1, int argx2, int argy2) {
                return Math.sqrt((argx1 - argx2) * (argx1 - argx2) + (argy1 - argy2) * (argy1 - argy2));
            }

            // log2:  Logarithm base 2
            public double log2(double d) {
                return Math.log(d) / Math.log(2.0);
            }

            /*METODI PER I VETTORI*/
            public double[] diff(double[] z) {
                double first = z[0];
                double ultimade = z[z.length - 1];
                double[] result = new double[z.length];

                for (int i = 0; i < z.length; i++) {
                    if (i == (z.length - 1)) {
                        result[i] = (ultimade - first);
                    } else {
                        result[i] = (z[i + 1] - z[i]);
                    }
                }
                return result;
            }

            public double[] diffVector(double[] a, double[] b) {
                double[] result = new double[a.length];
                for (int i = 0; i < a.length; i++) {
                    result[i] = a[i] - b[i];
                }

                return result;
            }

            public double[] moltVector(double[] a, double[] b) { //ok
                double[] result = new double[a.length];
                for (int i = 0; i < a.length; i++) {
                    result[i] = a[i] * b[i];
                }

                return result;
            }

            public double[] divVector(double[] a, double[] b) {
                double[] result = new double[a.length];
                for (int i = 0; i < a.length; i++) {
                    result[i] = a[i] * Math.pow(b[i], -1);
                }
                return result;
            }

            public double[] sumVector(double[] a, double[] b) {
                double[] result = new double[a.length];
                for (int i = 0; i < a.length; i++) {
                    result[i] = (a[i] + b[i]);
                }
                return result;
            }

            public double[] elevationVector(double[] a, double elevation) {
                double[] result = new double[a.length];
                for (int i = 0; i < a.length; i++) {
                    result[i] = Math.pow(a[i], elevation);
                }
                return result;
            }

            /* Metodi Giorgia */
            private double[] getRadiiValues(Roi roi, double x_cg, double y_cg){
                Polygon p = roi.getPolygon();
                int n = p.npoints;
                int i;
                int impHeight = imp.getHeight();
                double sumR = 0;
                double variance = 0;
                double[] radii = new double[n];
                double[] radiiValues = new double[5]; //minR, maxR, media, variance, stdDev
                radiiValues[0] = Double.MAX_VALUE; //min
                radiiValues[1] = -1; //max

                for(i = 0; i < n; i++){
                    radii[i] = distance((int)x_cg, (int)y_cg, p.xpoints[i],impHeight -p.ypoints[i]);

                    if(radii[i] < radiiValues[0])
                        radiiValues[0] = radii[i];
                    if(radii[i] > radiiValues[1])
                        radiiValues[1] = radii[i];
                    sumR+= radii[i];
                }

                double media = sumR/n;
                radiiValues[2] = media;

                for(i = 0; i < n; i++){
                    variance += (radii[i] - media) * (radii[i] - media);
                }
                variance /= (n - 1);
                radiiValues[3] = variance;
                radiiValues[4] = Math.sqrt(variance); //std deviation
                return  radiiValues;
            }

            private double getDS(Roi roi, int[] is, double x_cg, double y_cg){
                //double ds = Math.sqrt(Math.pow((is[0] - x_cg), 2.0) + Math.pow((is[1] - y_cg), 2.0));
                Point2D.Double pIs = new Point2D.Double(is[0], is[1]);
                Point2D.Double pCg = new Point2D.Double(x_cg, y_cg);
                double ds = pIs.distance(pCg);
                return ds;
            }

            private int[] getIS(Roi roi){
                int impHeight = imp.getHeight();
                int impWidth = imp.getWidth();
                Rectangle r = roi.getBounds();
                int bbCenterX = r.x + r.width/2;
                int bbCenterY = impHeight - r.y - r.height/2;
                int realX = r.x;
                int realY = impHeight - r.y;

                int[] startH = new int[2];
                int[] endH = new int[2];
                int[] startW = new int[2];
                int[] endW = new int[2];
                double s1, s2;
                int[] IS = new int[2];

                for(int i = realX; i < realX + r.width; i++) {
                    if (roi.contains(i, realY)) {
                        startH[0] = i;
                        startH[1] = realY;
                        break;
                    }
                }
                for(int i = realX; i < realX + r.width; i++){
                    if(roi.contains(i, realY - r.height)){
                        endH[0] = i;
                        endH[1] = realY - r.height;
                        break;
                    }
                }
                for(int i = realY - r.height; i < realY ; i++){
                    if(roi.contains(realX, i)){
                        startW[0] = realX;
                        startW[1] = i;
                        break;
                    }
                }
                for(int i = realY - r.height; i < realY ; i++){
                    if(roi.contains(realX + r.width, i)){
                        endW[0] = realX + r.width;
                        endW[1] = i;
                        break;
                    }
                }

                s1 = (0.5) * ((startW[0] - endW[0]) * (startH[1] - endW[1]) - (startW[1] - endW[1]) * (startH[0] - endW[0]));
                s2 = (0.5) * ((startW[0] - endW[0]) * (endW[1] - endH[1]) - (startW[1] - endW[1]) * (endW[0] - endH[0]));

                //IS[0] = (int)(startH[0] + (s1 * (endH[0] - startH[0]))/(s1 + s2));
                //IS[1] = (int)(startH[1] + (s1 * (endH[1] - startH[1]))/(s1 + s2));
                IS[0] = bbCenterX;
                IS[1] = bbCenterY;
                return IS;

            }
        }

    }