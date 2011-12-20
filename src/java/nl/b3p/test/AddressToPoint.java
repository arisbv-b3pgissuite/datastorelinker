package nl.b3p.test;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 *
 * @author Boy de Wit
 */
public class AddressToPoint {

    private static String googleBaseUrl = "http://maps.google.nl/maps/geo?q=";

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject json = new JSONObject(jsonText);
            return json;
        } finally {
            is.close();
        }
    }

    private static Point convertWktToRdsPoint(String wkt) {
        Point p = null;

        try {
            Geometry sourceGeometry = createGeomFromWKTString(wkt);

            if (sourceGeometry != null) {
                CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:4326");
                CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:28992");

                MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);

                if (transform != null) {
                    Geometry targetGeometry = JTS.transform(sourceGeometry, transform);

                    if (targetGeometry != null) {
                        targetGeometry.setSRID(4326);
                        p = targetGeometry.getCentroid();
                    }
                }
            }

        } catch (Exception ex) {
            System.out.println("Fout tijdens conversie wkt naar latlon: " + ex);
        }

        return p;
    }

    public static Geometry createGeomFromWKTString(String wktstring) throws Exception {
        WKTReader wktreader = new WKTReader(new GeometryFactory(new PrecisionModel(), 28992));
        try {
            return wktreader.read(wktstring);
        } catch (ParseException ex) {
            throw new Exception(ex);
        }

    }

    public static void main(String[] args) {

        // Voorbeel verzoek
        // http://maps.google.nl/maps/geo?q=tak+van+poortvlietstraat+12,+Den+Haag&hl=nl
        
        String adres = "tak van poortvlietstraat 12";
        String plaats = ",+Den Haag";
        String hl = "&hl=nl";
        String output = "&output=json";

        JSONObject json;
        try {
            String encodedParams = URLEncoder.encode(adres + plaats, "UTF-8");
            String otherParams = hl + output;

            String url = googleBaseUrl + encodedParams + otherParams;

            json = readJsonFromUrl(url);

            Double x = (Double) json.getJSONArray("Placemark").getJSONObject(0).getJSONObject("Point").getJSONArray("coordinates").get(0);
            Double y = (Double) json.getJSONArray("Placemark").getJSONObject(0).getJSONObject("Point").getJSONArray("coordinates").get(1);

            /* Lat is in nederland groter dan lon. Ongeveer Lon 4 en Lat 52 */
            try {
                Double lat = x;
                Double lon = y;

                if (x > y) {
                    lat = x;
                    lon = y;
                } else {
                    lat = y;
                    lon = x;
                }

                Point p1 = convertWktToRdsPoint("POINT("+lat+" " +lon+")");

                if (p1 != null) {
                    System.out.println(p1.toText());
                }

            } catch (Exception ex) {
                Logger.getLogger(AddressToPoint.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } catch (IOException ex) {
            Logger.getLogger(AddressToPoint.class.getName()).log(Level.SEVERE, null, ex);
        } catch (JSONException ex) {
            Logger.getLogger(AddressToPoint.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}