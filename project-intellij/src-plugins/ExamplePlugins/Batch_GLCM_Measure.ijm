// Batch GLCM Measure
//
// This macro is a wrapper for Julio E. Cabrera's GLCM_Texture class.
// It allows you to batch process all the images in a specified directory.
// The macro will ask you for the step size in pixels, and then repeatedly
// call the GLCM_Texture plugin on every image in the directory.
// Since the original class resets the results table every time a
// new image is processed, the output of this macro is sent to the Log window.
// Values are separated by commas (CSV format) for easy processing.
//
// Version .1
// July 25, 2007
//
// Feel free to distribute and modify this macro freely.

macro "Batch GLCM Measure" {
    dir = getDirectory("Choose a Directory ");
    list = getFileList(dir);
    step = getNumber("Enter the size of the step in pixels: ", 1);
    setBatchMode(true);
    print("#,","Angular Second Moment,","Contrast,","Correlation,","Inverse Difference Moment,","Entropy,");
    for (i=0; i<list.length; i++) {
        path = dir+list[i];
        showProgress(i, list.length);
        if (!endsWith(path,"/")) open(path);
        if (nImages>=1) {
            run("GLCM TextureToo", "enter="+step+ " select=[0 degrees] angular contrast correlation inverse entropy");
            close();
            asm = getResult("Angular Second Moment",0);
            contrast = getResult("Contrast",0);
            correlation = getResult("Correlation",0);
            idm = getResult("Inverse Difference Moment   ",0); //Extra spaces needed due to source code error
            entropy = getResult("Entropy",0);
            print(list[i],",",asm,",",contrast,",",correlation,",",idm,",",entropy);
        }
    }
}
