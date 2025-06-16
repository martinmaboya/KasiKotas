// src/main/java/kasiKotas/service/EmailService.java
package kasiKotas.service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import kasiKotas.model.Order;
import kasiKotas.model.OrderItem;
import kasiKotas.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Service class for sending emails and generating PDF attachments.
 * This service uses Spring's JavaMailSender to send emails and OpenPDF (com.lowagie.text)
 * to create dynamic PDF documents for order confirmations and admin notifications.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an email with a PDF attachment.
     *
     * @param to The recipient's email address.
     * @param subject The subject of the email.
     * @param body The plain text body of the email.
     * @param pdfBytes The byte array of the PDF document to attach.
     * @param attachmentFilename The filename for the PDF attachment (e.g., "OrderConfirmation.pdf").
     * @throws MessagingException if there's an error creating or sending the message.
     */
    public void sendEmailWithAttachment(String to, String subject, String body, byte[] pdfBytes, String attachmentFilename) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true); // true indicates multipart message (for attachment)

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body);

        // Attach the PDF from the byte array
        helper.addAttachment(attachmentFilename, new ByteArrayDataSource(pdfBytes, "application/pdf"));

        mailSender.send(message);
        System.out.println("Email sent successfully to: " + to + " with attachment: " + attachmentFilename);
    }

    /**
     * Generates a PDF document for a customer's order confirmation.
     *
     * @param order The Order object containing details.
     * @param customer The User object of the customer.
     * @return A byte array representing the generated PDF document.
     * @throws Exception if there's an error during PDF generation.
     */
    public byte[] generateCustomerOrderPdf(Order order, User customer) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, baos); // Get instance for PDF writer
        document.open(); // Open the document for writing

        // Add content to the PDF
        document.add(new Paragraph("KasiKotas Order Confirmation"));
        document.add(new Paragraph("------------------------------------"));
        document.add(new Paragraph("Hello, " + (customer.getFirstName() != null ? customer.getFirstName() : "Customer") + "!"));
        document.add(new Paragraph("Thank you for your order. Your order details are below:"));
        document.add(new Paragraph(" ")); // Blank line

        document.add(new Paragraph("Order ID: " + (order.getId() != null ? order.getId() : "N/A")));
        document.add(new Paragraph("Order Date: " + (order.getOrderDate() != null ? order.getOrderDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : "N/A")));
        document.add(new Paragraph("Total Amount: R" + (order.getTotalAmount() != null ? String.format("%.2f", order.getTotalAmount()) : "0.00")));
        document.add(new Paragraph("Status: " + (order.getStatus() != null ? order.getStatus().name() : "UNKNOWN")));
        document.add(new Paragraph("Shipping Address: " + (order.getShippingAddress() != null ? order.getShippingAddress() : "N/A")));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Ordered Items:"));
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            for (OrderItem item : order.getOrderItems()) {
                document.add(new Paragraph("- " + (item.getProduct() != null && item.getProduct().getName() != null ? item.getProduct().getName() : "Unknown Product") +
                        " x " + (item.getQuantity() != null ? item.getQuantity() : "0") +
                        " @ R" + (item.getPriceAtTimeOfOrder() != null ? String.format("%.2f", item.getPriceAtTimeOfOrder()) : "0.00") +
                        " each"));
            }
        } else {
            document.add(new Paragraph("No items found for this order."));
        }
        document.add(new Paragraph(" "));
        document.add(new Paragraph("We will notify you once your order is processed."));
        document.add(new Paragraph("Thank you for choosing KasiKotas!"));

        document.close(); // Close the document to finalize PDF writing
        return baos.toByteArray(); // Return the PDF as a byte array
    }

    /**
     * Generates a PDF document for admin notification when a new order is placed.
     *
     * @param order The Order object containing details.
     * @return A byte array representing the generated PDF document.
     * @throws Exception if there's an error during PDF generation.
     */
    public byte[] generateAdminOrderNotificationPdf(Order order) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, baos);
        document.open();

        document.add(new Paragraph("New KasiKotas Order Notification"));
        document.add(new Paragraph("------------------------------------"));
        document.add(new Paragraph("A new order has been placed on KasiKotas."));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Order ID: " + (order.getId() != null ? order.getId() : "N/A")));
        document.add(new Paragraph("Order Date: " + (order.getOrderDate() != null ? order.getOrderDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : "N/A")));
        document.add(new Paragraph("Customer Email: " + (order.getUser() != null && order.getUser().getEmail() != null ? order.getUser().getEmail() : "N/A")));
        document.add(new Paragraph("Customer Name: " + (order.getUser() != null && order.getUser().getFirstName() != null ? order.getUser().getFirstName() + " " + order.getUser().getLastName() : "N/A")));
        document.add(new Paragraph("Shipping Address: " + (order.getShippingAddress() != null ? order.getShippingAddress() : "N/A")));
        document.add(new Paragraph("Total Amount: R" + (order.getTotalAmount() != null ? String.format("%.2f", order.getTotalAmount()) : "0.00")));
        document.add(new Paragraph("Status: " + (order.getStatus() != null ? order.getStatus().name() : "UNKNOWN")));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Ordered Items:"));
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            for (OrderItem item : order.getOrderItems()) {
                document.add(new Paragraph("- " + (item.getProduct() != null && item.getProduct().getName() != null ? item.getProduct().getName() : "Unknown Product") +
                        " x " + (item.getQuantity() != null ? item.getQuantity() : "0") +
                        " @ R" + (item.getPriceAtTimeOfOrder() != null ? String.format("%.2f", item.getPriceAtTimeOfOrder()) : "0.00") +
                        " each"));
            }
        } else {
            document.add(new Paragraph("No items found for this order."));
        }
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Please process this order accordingly."));

        document.close();
        return baos.toByteArray();
    }
}
