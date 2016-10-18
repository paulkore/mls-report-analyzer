package ca.stackrabbit.mls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MLSAnalyzer {

    public MLSAnalyzer() {

    }

    public void analyze(String filename) {
        if (Strings.isEmpty(filename)) {
            throw new IllegalArgumentException("Filename can't be empty");
        }
        System.out.println("Analyzer initializing for input filename: " + filename);

        File inputFile = getInputFile(filename);
        List<Listing> listings = extractListingData(inputFile);

        File outputFile = getOutputFile(inputFile);
        writeOutput(outputFile, listings);
    }

    private File getInputFile(String filename) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL inputResource = classLoader.getResource(filename);
        if (inputResource == null) {
            throw new RuntimeException("Resource not found: " + filename);
        }
        File inputFile = new File(inputResource.getFile());
        System.out.println("Input file: " + inputFile.getAbsolutePath());
        return inputFile;
    }

    private File getOutputFile(File inputFile) {
        String inputFilename = inputFile.getName().replaceAll("\\.html", "");

        File outputFile;

        int i=0;
        while (true) {
            i++;
            String filename = "output_" + inputFilename + "_" + i + ".csv";
            outputFile = new File(inputFile.getParentFile(), filename);
            if (outputFile.exists()) {
                continue;
            }
            break;
        }

        final boolean createSuccess;
        try {
            createSuccess = outputFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!createSuccess) {
            throw new RuntimeException("Unable to create output file: " + outputFile.getAbsolutePath());
        }

        System.out.println("Output file: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    private PrintWriter openWriter(File outputFile) {
        PrintWriter writer;
        try {
            writer = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return writer;
    }

    private List<Listing> extractListingData(File inputFile) {
        System.out.println("Parsing HTML document...");

        final Document doc;
        try {
            doc = Jsoup.parse(inputFile, "UTF-8");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Extracting listing data...");

        Elements allListings = doc.body().select("div.reports > div.link-item");

        List<Listing> listingsInOrder = new ArrayList<>();
        Map<String, List<Listing>> listingsByProperty = new HashMap<>();

        int listingIndex=0;
        for (Element listingRoot : allListings) {
            Listing listing = Listing.extract(++listingIndex, listingRoot);
            listingsInOrder.add(listing);

            String propertyAddress = listing.getPropertyAddress();
            if (Strings.isEmpty(propertyAddress)) {
                throw new RuntimeException("Property address unavailable");
            }

            List<Listing> propertyListings = listingsByProperty.get(propertyAddress);
            if (propertyListings == null) {
                // this is the first time that we encounter a listing for this property
                propertyListings = new ArrayList<>();
                listingsByProperty.put(propertyAddress, propertyListings);
                propertyListings.add(listing);
            }
            else {
                // this property is repeated
                propertyListings.add(listing);
                for (Listing repeatedListing : propertyListings) {
                    repeatedListing.setRepeated(true);
                }
            }
        }

        return listingsInOrder;
    }

    private void writeOutput(File outputFile, List<Listing> listings) {
        PrintWriter out = openWriter(outputFile);
        out.println(Listing.CSV_HEADERS);

        for (Listing listing : listings) {
            out.println(listing.csvLine());
        }

        out.flush();
        out.close();
    }

}
