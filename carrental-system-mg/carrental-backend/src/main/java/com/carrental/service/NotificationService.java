package com.carrental.service;

import com.carrental.entity.Bill;
import com.carrental.entity.Booking;
import com.carrental.entity.User;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final PdfGeneratorService pdfGeneratorService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${twilio.account.sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token:}")
    private String twilioAuthToken;

    @Value("${twilio.whatsapp.from:}")
    private String twilioWhatsappFrom;

    @PostConstruct
    public void initTwilio() {
        try {
            if (twilioAccountSid != null && !twilioAccountSid.isEmpty()) {
                Twilio.init(twilioAccountSid, twilioAuthToken);
            }
        } catch (Throwable t) {
            System.err.println("Critical failure initializing Twilio: " + t.getMessage());
        }
    }

    public void sendBookingConfirmation(Booking booking) {
        User customer = booking.getCustomer();
        com.carrental.entity.Vehicle vehicle = booking.getVehicle();

        String subject = "🚗 Booking Confirmed - " + booking.getBookingNumber() + " | MotoGlide";

        long days = java.time.temporal.ChronoUnit.DAYS.between(booking.getPickupDate(), booking.getReturnDate());
        if (days < 1) days = 1;

        java.math.BigDecimal advanceDue = booking.getAdvanceAmount() != null ? booking.getAdvanceAmount()
                : booking.getTotalAmount().divide(java.math.BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal remaining = booking.getTotalAmount().subtract(advanceDue);

        String htmlBody = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;'>"
            + "<div style='max-width:600px;margin:30px auto;background:white;border-radius:12px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,0.1);'>"
            // Header
            + "<div style='background:linear-gradient(135deg,#1a73e8,#0d47a1);padding:35px 30px;text-align:center;'>"
            + "<h1 style='color:white;margin:0;font-size:26px;letter-spacing:1px;font-weight:900;'>🚗 Booking Confirmed!</h1>"
            + "<p style='color:rgba(255,255,255,0.9);margin:10px 0 0;font-size:15px;'>Your premium car from MotoGlide is ready for you</p>"
            + "</div>"
            // Greeting
            + "<div style='padding:30px 30px 10px;'>"
            + "<p style='font-size:16px;color:#333;'>Hello <strong>" + customer.getFirstName() + "</strong>,</p>"
            + "<p style='color:#666;font-size:14px;'>Thank you for choosing MotoGlide. Here is your receipt:</p>"
            + "</div>"
            // Booking Details Section (Violet/Indigo Theme)
            + "<div style='margin:0 30px;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 6px rgba(0,0,0,0.02);'>"
            + "<div style='background:linear-gradient(to right, #4f46e5, #4338ca);padding:14px 20px;'><p style='color:white;margin:0;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;'>📋 BOOKING DETAILS</p></div>"
            + "<table style='width:100%;border-collapse:collapse;font-size:14px;'>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;width:40%;'>Booking ID</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>#" + booking.getBookingNumber() + "</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>Car</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + vehicle.getMake() + " " + vehicle.getModel() + " (" + vehicle.getYear() + ")</td></tr>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Period</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + booking.getPickupDate() + " to " + booking.getReturnDate() + " (" + days + " day" + (days > 1 ? "s" : "") + ")</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>Pickup Time</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + booking.getPickupTime() + "</td></tr>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>City</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + booking.getCity().getName() + "</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>Status</td><td style='padding:14px 20px;font-weight:900;color:#10b981;text-transform:uppercase;'>CONFIRMED ✓</td></tr>"
            + "</table></div>"
            // Payment Summary Section (Green Theme)
            + "<div style='margin:25px 30px;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 6px rgba(0,0,0,0.02);'>"
            + "<div style='background:linear-gradient(to right, #10b981, #059669);padding:14px 20px;'><p style='color:white;margin:0;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;'>💳 PAYMENT SUMMARY</p></div>"
            + "<table style='width:100%;border-collapse:collapse;font-size:14px;'>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Total Amount</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>Rs." + String.format("%.2f", booking.getTotalAmount()) + "</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>Advance Paid</td><td style='padding:14px 20px;font-weight:900;color:#10b981;'>Rs." + String.format("%.2f", advanceDue) + "</td></tr>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Remaining Balance</td><td style='padding:14px 20px;font-weight:900;color:#e11d48;'>Rs." + String.format("%.2f", remaining) + "</td></tr>"
            + "</table></div>"
            // Invoice Note
            + "<div style='margin:20px 30px;padding:16px 20px;border-left:4px solid #1a73e8;background:#e8f0fe;border-radius:0 8px 8px 0;'>"
            + "<p style='margin:0;color:#1a73e8;font-size:13px;font-weight:bold;'>📄 Invoice Generated</p>"
            + "<p style='margin:6px 0 0;font-size:13px;color:#555;'>Your detailed invoice is attached to this email as a PDF. You can also view it in your dashboard.</p>"
            + "</div>"
            // Footer
            + "<div style='padding:20px 30px;text-align:center;background:#f9f9f9;border-top:1px solid #eee;margin-top:20px;'>"
            + "<p style='color:#27ae60;font-size:16px;font-weight:bold;margin:0 0 8px;'>Thank you for riding with us! 🎉</p>"
            + "<p style='color:#999;font-size:12px;margin:0;'>For support, contact us at support@motoglide.com</p>"
            + "</div>"
            + "</div></body></html>";

        // Create a fake payment and bill for the invoice PDF (booking-level only)
        com.carrental.entity.Payment dummyAdvancePayment = new com.carrental.entity.Payment();
        dummyAdvancePayment.setAmount(advanceDue);
        dummyAdvancePayment.setPaymentType(com.carrental.entity.Payment.PaymentType.ADVANCE);
        dummyAdvancePayment.setTransactionId("PENDING");
        dummyAdvancePayment.setPaymentDate(java.time.LocalDateTime.now());
        dummyAdvancePayment.setStatus(com.carrental.entity.Payment.PaymentStatus.PENDING);

        try {
            byte[] pdfBytes = pdfGeneratorService.generateInvoicePdf(booking, dummyAdvancePayment, null);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(customer.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = isHtml
            helper.addAttachment("Invoice-" + booking.getBookingNumber() + ".pdf", new ByteArrayResource(pdfBytes));
            mailSender.send(mimeMessage);
        } catch (Throwable e) {
            System.err.println("Error sending HTML booking confirmation with PDF: " + e.getMessage());
            // Fallback: plain text
            sendEmail(customer.getEmail(), subject,
                "Dear " + customer.getFirstName() + ",\n\nYour booking " + booking.getBookingNumber() + " is confirmed.\n"
                + "Period: " + booking.getPickupDate() + " to " + booking.getReturnDate() + "\n"
                + "Total: Rs. " + booking.getTotalAmount() + "\n\nThank you for choosing MotoGlide!");
        }
        sendBookingConfirmationWhatsApp(booking);
    }

    public void sendPaymentReminder(Booking booking) {
        User customer = booking.getCustomer();

        String subject = "Payment Reminder - Car Rental System";
        String body = String.format(
                "Dear %s,\n\nThis is a reminder to pay the remaining amount for your booking.\n" +
                "Booking Number: %s\n" +
                "Amount Due: Rs. %.2f\n" +
                "Due Date: 10 days before rental\n\n" +
                "Please complete the payment to confirm your booking.",
                customer.getFirstName(),
                booking.getBookingNumber(),
                booking.getAdvanceAmount()
        );

        sendEmail(customer.getEmail(), subject, body);
    }

    public void sendBillEmail(Bill bill) {
        User customer = bill.getCustomer();

        String subject = "Bill Generated - Car Rental System";
        String body = String.format(
                "Dear %s,\n\nYour rental bill has been generated.\n" +
                "Bill Number: %s\n" +
                "Total Amount: Rs. %.2f\n" +
                "Amount Due: Rs. %.2f\n\n" +
                "Please find the detailed bill attached.",
                customer.getFirstName(),
                bill.getBillNumber(),
                bill.getTotalAmount(),
                bill.getBalanceAmount()
        );

        sendEmail(customer.getEmail(), subject, body);
    }

    public void sendPaymentConfirmationWithInvoice(com.carrental.entity.Booking booking, 
                                                     com.carrental.entity.Payment payment,
                                                     Bill bill) {
        User customer = booking.getCustomer();
        com.carrental.entity.Vehicle vehicle = booking.getVehicle();
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Dear %s,\n\n", customer.getFirstName()));
        sb.append("✅ Your payment has been processed successfully!\n\n");
        sb.append("══════════════════════════════════════\n");
        sb.append("          PAYMENT CONFIRMATION        \n");
        sb.append("══════════════════════════════════════\n\n");
        
        // Booking details
        sb.append(String.format("Booking Number  : %s\n", booking.getBookingNumber()));
        sb.append(String.format("Vehicle         : %s %s (%d)\n", vehicle.getMake(), vehicle.getModel(), vehicle.getYear()));
        sb.append(String.format("Category        : %s\n", vehicle.getCategory().getName()));
        sb.append(String.format("Transmission    : %s\n", vehicle.getTransmission()));
        sb.append(String.format("City            : %s\n", booking.getCity().getName()));
        sb.append(String.format("Rental Period   : %s to %s\n", booking.getPickupDate(), booking.getReturnDate()));
        sb.append(String.format("Pickup Time     : %s\n\n", booking.getPickupTime()));
        
        // Cost breakdown
        sb.append("── Cost Breakdown ───────────────────\n");
        sb.append(String.format("Rental Amount   : Rs. %.2f\n", booking.getRentalAmount()));
        
        if (booking.getExtraCharges() != null && booking.getExtraCharges().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("Add-ons         : Rs. %.2f\n", booking.getExtraCharges()));
            if (Boolean.TRUE.equals(booking.getIncludeInsurance())) sb.append("  • Insurance (Rs. 150/day)\n");
            if (Boolean.TRUE.equals(booking.getIncludeGps())) sb.append("  • GPS Navigation (Rs. 50/day)\n");
            if (Boolean.TRUE.equals(booking.getIncludeChildSeat())) sb.append("  • Child Seat (Rs. 75/day)\n");
            if (booking.getAdditionalDrivers() != null && booking.getAdditionalDrivers() > 0)
                sb.append(String.format("  • Additional Drivers: %d (Rs. 100/day each)\n", booking.getAdditionalDrivers()));
        }
        
        if (booking.getDeliveryFee() != null && booking.getDeliveryFee().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("Delivery Fee    : Rs. %.2f\n", booking.getDeliveryFee()));
        }
        
        if (booking.getDiscountAmount() != null && booking.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("Discount (%d%%)   : -Rs. %.2f\n", booking.getDiscountPercentage(), booking.getDiscountAmount()));
        }
        
        if (booking.getCouponCode() != null && !booking.getCouponCode().isEmpty()) {
            sb.append(String.format("Coupon (%s)  : -Rs. %.2f\n", booking.getCouponCode(), booking.getCouponDiscount()));
        }
        
        sb.append("─────────────────────────────────────\n");
        sb.append(String.format("TOTAL COST      : Rs. %.2f\n", booking.getTotalAmount()));
        
        // Payment details
        sb.append("── Payment Details ──────────────────\n");
        if (payment.getAmount().compareTo(booking.getTotalAmount()) < 0) {
            sb.append(String.format("AMOUNT PAID (THIS TXN) : Rs. %.2f\n", payment.getAmount()));
            sb.append(String.format("REMAINING BALANCE DUE  : Rs. %.2f\n", booking.getTotalAmount().subtract(payment.getAmount())));
        } else {
            sb.append(String.format("AMOUNT PAID SUM : Rs. %.2f\n", payment.getAmount()));
            sb.append(String.format("REMAINING BALANCE DUE  : Rs. 0.00\n"));
        }
        sb.append(String.format("Transaction ID  : %s\n", payment.getTransactionId()));
        sb.append(String.format("Payment Method  : %s\n", payment.getPaymentMethod()));
        sb.append(String.format("Payment Date    : %s\n", payment.getPaymentDate()));
        sb.append(String.format("Status          : %s\n\n", payment.getStatus()));
        
        // Invoice info  
        if (bill != null) {
            sb.append(String.format("Invoice Number  : %s\n\n", bill.getBillNumber()));
        }
        
        sb.append("══════════════════════════════════════\n\n");
        sb.append("Your booking is now CONFIRMED! The vehicle will be ready at the scheduled pickup time.\n\n");
        sb.append("Thank you for choosing MotoGlide Car Rental!\n");
        sb.append("For support, contact us at support@motoglide.com\n");

        String subject = "🚗 Payment Confirmed - Booking " + booking.getBookingNumber() + " | MotoGlide";
        
        try {
            byte[] pdfBytes = pdfGeneratorService.generateInvoicePdf(booking, payment, bill);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(customer.getEmail());
            helper.setSubject(subject);
            helper.setText(sb.toString());
            
            helper.addAttachment("Invoice-" + booking.getBookingNumber() + ".pdf", new ByteArrayResource(pdfBytes));
            mailSender.send(message);
        } catch (Throwable e) {
            System.err.println("Error sending email with PDF invoice: " + e.getMessage());
            // Fallback to simple email
            sendEmail(customer.getEmail(), subject, sb.toString());
        }
        sendPaymentConfirmationWhatsApp(booking, payment, bill);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
        }
    }

    public void sendBookingCancellation(Booking booking) {
        User customer = booking.getCustomer();
        com.carrental.entity.Vehicle vehicle = booking.getVehicle();

        String subject = "🚫 Booking Cancelled - " + booking.getBookingNumber() + " | MotoGlide";

        long days = java.time.temporal.ChronoUnit.DAYS.between(booking.getPickupDate(), booking.getReturnDate());
        if (days < 1) days = 1;

        java.math.BigDecimal advancePaid = booking.getAdvanceAmount() != null ? booking.getAdvanceAmount()
                : booking.getTotalAmount().divide(java.math.BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
        
        String htmlBody = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;'>"
            + "<div style='max-width:600px;margin:30px auto;background:white;border-radius:12px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,0.1);'>"
            // Header
            + "<div style='background:linear-gradient(135deg,#1a73e8,#0d47a1);padding:35px 30px;text-align:center;'>"
            + "<h1 style='color:white;margin:0;font-size:26px;letter-spacing:1px;font-weight:900;'>🚫 Booking Cancelled</h1>"
            + "<p style='color:rgba(255,255,255,0.9);margin:10px 0 0;font-size:15px;'>Your reservation has been cancelled successfully</p>"
            + "</div>"
            // Greeting
            + "<div style='padding:30px 30px 10px;'>"
            + "<p style='font-size:16px;color:#333;'>Hello <strong>" + customer.getFirstName() + "</strong>,</p>"
            + "<p style='color:#666;font-size:14px;'>As per your request, your booking has been cancelled. Details below:</p>"
            + "</div>"
            // Booking Details Section (Violet/Indigo Theme)
            + "<div style='margin:0 30px;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 6px rgba(0,0,0,0.02);'>"
            + "<div style='background:linear-gradient(to right, #4f46e5, #4338ca);padding:14px 20px;'><p style='color:white;margin:0;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;'>📋 BOOKING DETAILS</p></div>"
            + "<table style='width:100%;border-collapse:collapse;font-size:14px;'>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;width:40%;'>Booking ID</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>#" + booking.getBookingNumber() + "</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>Car</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + vehicle.getMake() + " " + vehicle.getModel() + " (" + vehicle.getYear() + ")</td></tr>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Period</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + booking.getPickupDate() + " to " + booking.getReturnDate() + "</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>City</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>" + booking.getCity().getName() + "</td></tr>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Status</td><td style='padding:14px 20px;font-weight:900;color:#e11d48;text-transform:uppercase;'>CANCELLED ✘</td></tr>"
            + "</table></div>"
            // Refund Summary Section (Green Theme)
            + "<div style='margin:25px 30px;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 6px rgba(0,0,0,0.02);'>"
            + "<div style='background:linear-gradient(to right, #10b981, #059669);padding:14px 20px;'><p style='color:white;margin:0;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;'>💳 REFUND SUMMARY</p></div>"
            + "<table style='width:100%;border-collapse:collapse;font-size:14px;'>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Refund Initiated</td><td style='padding:14px 20px;font-weight:900;color:#10b981;'>Rs." + String.format("%.2f", advancePaid) + "</td></tr>"
            + "<tr><td style='padding:14px 20px;color:#64748b;'>Handling Fee</td><td style='padding:14px 20px;font-weight:800;color:#1e293b;'>Rs. 0.00</td></tr>"
            + "<tr style='background:#fcfdff;'><td style='padding:14px 20px;color:#64748b;'>Final Settlement</td><td style='padding:14px 20px;font-weight:900;color:#10b981;'>Rs." + String.format("%.2f", advancePaid) + "</td></tr>"
            + "</table></div>"
            // Footer
            + "<div style='padding:20px 30px;text-align:center;background:#f9f9f9;border-top:1px solid #eee;'>"
            + "<p style='color:#999;font-size:12px;margin:0;'>For any queries, please reply to this email.</p>"
            + "<p style='color:#999;font-size:12px;margin:5px 0 0;'>MotoGlide - Premium Car Rentals</p>"
            + "</div>"
            + "</div></body></html>";

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(customer.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(mimeMessage);
        } catch (Throwable e) {
            System.err.println("Error sending HTML cancellation email: " + e.getMessage());
            // Fallback: simplified plain text
            sendEmail(customer.getEmail(), subject, 
                "Dear " + customer.getFirstName() + ",\n\nYour booking " + booking.getBookingNumber() + " has been cancelled successfully.\n"
                + "Any advance payment of Rs." + advancePaid + " has been initiated for refund.\n\nThank you for choosing MotoGlide!");
        }
        sendBookingCancellationWhatsApp(booking);
    }

    public void sendBookingCompletion(Booking booking) {
        User customer = booking.getCustomer();
        String subject = "Rental Completed - Car Rental System";
        String body = String.format(
                "Dear %s,\n\nYour rental for booking %s has been completed.\n" +
                "Thank you for choosing MotoGlide!",
                customer.getFirstName(),
                booking.getBookingNumber()
        );
        sendEmail(customer.getEmail(), subject, body);
    }

    public void sendBookingConfirmationWhatsApp(Booking booking) {
        String message = String.format("🎉 Your booking %s is confirmed! View details at: http://localhost:3001/bookings", 
                booking.getBookingNumber());
        sendWhatsAppMessage(booking.getCustomer().getPhoneNumber(), message);
    }

    public void sendPaymentConfirmationWhatsApp(Booking booking, com.carrental.entity.Payment payment, Bill bill) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *PAYMENT CONFIRMED*\n");
        sb.append("Hi ").append(booking.getCustomer().getFirstName()).append(", your payment was successful!\n\n");
        sb.append("🧾 *BILL SUMMARY*\n");
        sb.append("Booking ID: ").append(booking.getBookingNumber()).append("\n");
        sb.append("Vehicle: ").append(booking.getVehicle().getMake()).append(" ").append(booking.getVehicle().getModel()).append("\n");
        sb.append("Amount Paid: Rs. ").append(payment.getAmount()).append("\n");
        if (payment.getAmount().compareTo(booking.getTotalAmount()) < 0) {
            sb.append("Remaining Balance Due: Rs. ").append(booking.getTotalAmount().subtract(payment.getAmount())).append("\n");
        }
        if (bill != null) {
            sb.append("Invoice No: ").append(bill.getBillNumber()).append("\n");
        }
        sb.append("\n");
        sb.append("🗓 *RENTAL PERIOD*\n");
        sb.append(booking.getPickupDate()).append(" to ").append(booking.getReturnDate()).append("\n\n");
        sb.append("A detailed PDF invoice has been sent to your email. ");
        sb.append("Thank you for choosing MotoGlide! Drive safe. 🚗");
        
        sendWhatsAppMessage(booking.getCustomer().getPhoneNumber(), sb.toString());
    }

    public void sendBookingCancellationWhatsApp(Booking booking) {
        String message = String.format("❌ Your booking %s has been cancelled. Any advance amount will be refunded. Thank you, MotoGlide.", 
                booking.getBookingNumber());
        sendWhatsAppMessage(booking.getCustomer().getPhoneNumber(), message);
    }

    public void sendOtpEmail(String email, String otp) {
        String subject = "📧 Verify Your Account - MotoGlide OTP";
        
        String htmlBody = "<!DOCTYPE html><html>"
            + "<head><meta charset='UTF-8'></head>"
            + "<body style='margin:0;padding:0;background-color:#f9fafb;font-family:\"Segoe UI\",Tahoma,Geneva,Verdana,sans-serif;'>"
            + "<div style='max-width:500px;margin:40px auto;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 10px 25px rgba(0,0,0,0.05);border:1px solid #e5e7eb;'>"
            // Gradient Header
            + "<div style='background:linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);padding:40px 20px;text-align:center;'>"
            + "<h1 style='color:#ffffff;margin:0;font-size:28px;font-weight:800;letter-spacing:-0.5px;'>Email Verification</h1>"
            + "<p style='color:rgba(255,255,255,0.9);margin:10px 0 0;font-size:15px;'>Car Rental System</p>"
            + "</div>"
            // Content
            + "<div style='padding:40px 35px;'>"
            + "<p style='font-size:16px;color:#374151;line-height:1.6;margin-top:0;'>Hello,</p>"
            + "<p style='font-size:15px;color:#4b5563;line-height:1.6;'>Thank you for choosing <strong>MotoGlide</strong>! To complete your registration and secure your account, please use the verification code below:</p>"
            
            // OTP Box
            + "<div style='margin:35px 0;text-align:center;background:#f3f4f6;border-radius:12px;padding:30px;border:2px dashed #6366f1;'>"
            + "<span style='font-family:monospace;font-size:42px;font-weight:900;letter-spacing:12px;color:#4f46e5;text-shadow:1px 1px 0px #fff;'>" + otp + "</span>"
            + "</div>"
            
            + "<p style='font-size:14px;color:#6b7280;text-align:center;'>This OTP is valid for <strong>" + 5 + " minutes</strong>. For security, please do not share this code with anyone.</p>"
            + "</div>"
            
            // Footer
            + "<div style='padding:25px;background:#f9fafb;text-align:center;border-top:1px solid #f3f4f6;'>"
            + "<p style='margin:0;font-size:12px;color:#9ca3af;'>If you didn't request this, you can safely ignore this email.</p>"
            + "<p style='margin:10px 0 0;font-size:12px;color:#9ca3af;font-weight:bold;'>© 2026 MotoGlide Car Rental</p>"
            + "</div>"
            + "</div></body></html>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send HTML OTP email: " + e.getMessage());
            // Fallback to simple
            sendEmail(email, subject, "Your MotoGlide verification code is: " + otp + "\nValid for 5 minutes.");
        }
    }

    public void sendWhatsAppMessage(String toPhoneNumber, String messageText) {
        try {
            if (twilioAccountSid == null || twilioAccountSid.isEmpty() || toPhoneNumber == null) {
                return;
            }
            // Ensure format is correct for WhatsApp
            String formattedTo = toPhoneNumber.startsWith("whatsapp:") ? toPhoneNumber : "whatsapp:" + toPhoneNumber;
            
            Message.creator(
                new PhoneNumber(formattedTo),
                new PhoneNumber(twilioWhatsappFrom),
                messageText
            ).create();
            System.out.println("WhatsApp message sent to " + toPhoneNumber);
        } catch (Throwable t) {
            System.err.println("Failed to send WhatsApp message (likely Twilio config or missing library): " + t.getMessage());
            // We catch Throwable to prevent NoClassDefFoundError from crashing the app if Twilio isn't loaded
        }
    }
    public void sendFinancialReport(String toEmail, byte[] pdfData) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("📊 MotoGlide - Financial Business Report | " + java.time.LocalDate.now());
            
            String html = "<html><body>"
                + "<h2 style='color:#1e3a8a;'>Detailed Financial Business Report</h2>"
                + "<p>Attached is the comprehensive financial report for MotoGlide Car Rental.</p>"
                + "<div style='background:#f3f4f6;padding:20px;border-radius:10px;'>"
                + "<p><strong>Report Date:</strong> " + java.time.LocalDateTime.now() + "</p>"
                + "<p>This report includes revenue breakdowns, periodic performance, and P&L summaries.</p>"
                + "</div>"
                + "<p style='color:#666;font-size:12px;margin-top:20px;'>Generated by MotoGlide Admin System.</p>"
                + "</body></html>";
                
            helper.setText(html, true);
            helper.addAttachment("MotoGlide_Financial_Report_" + java.time.LocalDate.now() + ".pdf", new ByteArrayResource(pdfData));
            
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
