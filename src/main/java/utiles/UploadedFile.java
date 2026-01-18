package utiles;

public class UploadedFile {
    private final String fieldName;        // nom du champ du formulaire (ex: "photo")
    private final String originalFileName; // nom original du fichier (ex: "mon-cv.pdf")
    private final String extension;        // ".pdf" ou ""
    private final byte[] content;          // contenu binaire

    public UploadedFile(String fieldName, String originalFileName, byte[] content) {
        this.fieldName = fieldName;
        this.originalFileName = originalFileName != null ? originalFileName : "unknown";
        this.content = content != null ? content : new byte[0];

        // Extraction de l'extension
        if (originalFileName != null && originalFileName.contains(".")) {
            int dotIndex = originalFileName.lastIndexOf('.');
            this.extension = dotIndex >= 0 ? originalFileName.substring(dotIndex) : "";
        } else {
            this.extension = "";
        }
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getExtension() {
        return extension;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "UploadedFile{fieldName='" + fieldName + "', originalFileName='" + originalFileName + "', extension='" + extension + "', size=" + content.length + " bytes}";
    }
}