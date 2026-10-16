import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

/** Nonpersonal fixtures. Run: java scripts/GeneratePersonalImageFixtures.java */
class GeneratePersonalImageFixtures {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("app/src/test/resources/personal-image-integrity");
        Files.createDirectories(root);
        for (boolean progressive : new boolean[]{false, true}) {
            for (boolean gray : new boolean[]{false, true}) {
                BufferedImage image = new BufferedImage(89, 107,
                    gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
                for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++)
                    image.setRGB(x,y, (((x*59+y*23)&255)<<16)|(((x*7+y*37)&255)<<8)|((x*43+y*11)&255));
                byte[] bytes = encode(image,"JPEG",progressive);
                verify(bytes);
                Files.write(root.resolve((progressive?"progressive":"baseline")+(gray?"-gray":"-rgb")+".jpg"),bytes);
            }
        }
        int[] types = {BufferedImage.TYPE_INT_ARGB,BufferedImage.TYPE_BYTE_INDEXED,
            BufferedImage.TYPE_BYTE_GRAY,BufferedImage.TYPE_BYTE_BINARY};
        String[] names = {"rgba","indexed","gray","binary"};
        for (boolean interlaced : new boolean[]{false,true}) for (int n=0;n<types.length;n++) {
            BufferedImage image = new BufferedImage(67,83,types[n]);
            for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++)
                image.setRGB(x,y,(x*12347+y*731)|0xff000000);
            byte[] bytes = encode(image,"PNG",interlaced);
            verify(bytes);
            Files.write(root.resolve(names[n]+(interlaced?"-adam7":"")+".png"),bytes);
        }
        BufferedImage oversized = new BufferedImage(6000,6001,BufferedImage.TYPE_BYTE_GRAY);
        for (String format : new String[]{"JPEG","PNG"}) {
            byte[] bytes=encode(oversized,format,false);
            verify(bytes);
            Files.write(root.resolve(format.equals("JPEG")?"over-36mp.jpg":"over-36mp.png"),bytes);
        }
        byte[] sparse=sparseProgressive();
        verify(sparse);
        Files.write(root.resolve("over-work-budget.jpg"),sparse);
        System.out.println("Generated and ImageIO-decoded 15 nonpersonal JPEG/PNG fixtures in "+root);
    }
    private static byte[] sparseProgressive() throws Exception {
        var out=new ByteArrayOutputStream(); out.write(new byte[]{(byte)255,(byte)0xd8});
        byte[] quant=new byte[65]; java.util.Arrays.fill(quant,(byte)1); quant[0]=0;
        marker(out,0xdb,quant);
        var tables=new ByteArrayOutputStream(); tables.write(0); tables.write(1); tables.write(new byte[15]); tables.write(0);
        tables.write(0x10); tables.write(new byte[]{0,0,0,15}); tables.write(new byte[12]);
        for (int i=0;i<15;i++) tables.write(i<<4);
        marker(out,0xc4,tables.toByteArray());
        marker(out,0xc2,new byte[]{8,0x17,0x70,0x17,0x70,1,1,0x11,0}); // 6000x6000 grayscale
        marker(out,0xda,new byte[]{1,1,0,0,0,0});
        out.write(bits("0".repeat(750*750)));
        var ac=new StringBuilder();
        for (int left=750*750;left>0;) {
            int run=Math.min(left,32767), r=31-Integer.numberOfLeadingZeros(run);
            ac.append(String.format("%4s",Integer.toBinaryString(r)).replace(' ','0'));
            if (r>0) ac.append(String.format("%"+r+"s",Integer.toBinaryString(run-(1<<r))).replace(' ','0'));
            left-=run;
        }
        byte[] entropy=bits(ac.toString());
        marker(out,0xda,new byte[]{1,1,0,1,63,13}); out.write(entropy);
        for (int high=13;high>0;high--) {
            marker(out,0xda,new byte[]{1,1,0,1,63,(byte)((high<<4)|(high-1))}); out.write(entropy);
        }
        out.write(new byte[]{(byte)255,(byte)0xd9}); return out.toByteArray();
    }
    private static byte[] bits(String value) {
        var out=new ByteArrayOutputStream();
        value += "1".repeat((8-value.length()%8)%8);
        for (int i=0;i<value.length();i+=8) {
            int b=Integer.parseInt(value.substring(i,i+8),2); out.write(b); if (b==255) out.write(0);
        }
        return out.toByteArray();
    }
    private static void marker(ByteArrayOutputStream out,int marker,byte[] payload) throws Exception {
        var data=new java.io.DataOutputStream(out); data.writeByte(255); data.writeByte(marker);
        data.writeShort(payload.length+2); data.write(payload);
    }
    private static byte[] encode(BufferedImage image,String format,boolean progressive) throws Exception {
        var writer=ImageIO.getImageWritersByFormatName(format).next();
        var bytes=new ByteArrayOutputStream();
        try (var output=ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            var params=writer.getDefaultWriteParam();
            if (format.equals("JPEG")) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(.87f);
            }
            params.setProgressiveMode(progressive?ImageWriteParam.MODE_DEFAULT:ImageWriteParam.MODE_DISABLED);
            writer.write(null,new IIOImage(image,null,null),params);
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }
    private static void verify(byte[] bytes) throws Exception {
        if (ImageIO.read(new java.io.ByteArrayInputStream(bytes))==null)
            throw new IllegalStateException("Generated image is not independently decodable");
    }
}
