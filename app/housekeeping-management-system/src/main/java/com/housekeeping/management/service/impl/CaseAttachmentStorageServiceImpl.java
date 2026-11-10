package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.CaseAttachmentStorageService;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CaseAttachmentStorageServiceImpl implements CaseAttachmentStorageService {
    private static final long MAX_BYTES=5L*1024*1024;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Path root;
    private final AccountAuthorizationService authorization;
    public CaseAttachmentStorageServiceImpl(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Value("${app.evidence.storage-root:./var/evidence}") String root, AccountAuthorizationService authorization) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.root=Path.of(root).toAbsolutePath().normalize(); this.authorization=authorization;
    }

    @Override
    public void uploadCustomerEvidence(Long caseId, Long bookingId, Long customerId, MultipartFile upload) {
        Long initialMessage=jdbc.query("""
                SELECT TOP (1) message_id FROM dbo.Feedback_Case_Messages
                WHERE case_id=? AND sender_role='CUSTOMER' ORDER BY message_id
                """,rs -> rs.next()?rs.getLong(1):null,caseId);
        if(initialMessage==null) throw new IllegalStateException("Chưa có tin nhắn đầu tiên của hồ sơ sự cố.");
        uploadCustomerMessageEvidence(caseId,bookingId,customerId,initialMessage,upload);
    }

    @Override
    public void uploadCustomerMessageEvidence(Long caseId, Long bookingId, Long customerId, Long messageId, MultipartFile upload) {
        uploadCustomerMessageEvidence(caseId,bookingId,customerId,messageId,java.util.List.of(upload));
    }

    @Override
    public void uploadCustomerMessageEvidence(Long caseId, Long bookingId, Long customerId, Long messageId, java.util.List<MultipartFile> uploads) {
        storeMessageEvidence(caseId,messageId,customerId,false,bookingId,uploads);
    }

    @Override
    public void uploadAdminMessageEvidence(Long caseId, Long adminId, Long messageId, MultipartFile upload) {
        authorization.requireActiveAdmin(adminId);
        uploadAdminMessageEvidence(caseId,adminId,messageId,java.util.List.of(upload));
    }

    @Override
    public void uploadAdminMessageEvidence(Long caseId, Long adminId, Long messageId, java.util.List<MultipartFile> uploads) {
        authorization.requireActiveAdmin(adminId);
        storeMessageEvidence(caseId,messageId,adminId,true,null,uploads);
    }

    private void storeMessageEvidence(Long caseId, Long messageId, Long actorId, boolean admin, Long bookingId, java.util.List<MultipartFile> uploads) {
        if(uploads==null || uploads.isEmpty() || uploads.size()>5 || uploads.stream().anyMatch(file->file==null || file.isEmpty()))
            throw new IllegalArgumentException("Mỗi tin nhắn cần có từ 1 đến 5 ảnh hợp lệ.");
        java.util.List<String> paths=new java.util.ArrayList<>();
        try {
            for(MultipartFile upload:uploads) paths.add(store(upload,false));
            tx.executeWithoutResult(status -> {
                Integer allowed=jdbc.queryForObject("""
                        SELECT COUNT(*) FROM dbo.Feedback_Cases c
                        JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                        JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                        JOIN dbo.Feedback_Case_Messages m ON m.case_id=c.case_id
                        WHERE c.case_id=? AND m.message_id=? AND m.sender_user_id=? AND m.sender_role=?
                          AND c.case_status<>'RESOLVED' AND (?=1 OR (b.booking_id=? AND f.customer_id=? AND b.customer_id=?))
                        """,Integer.class,caseId,messageId,actorId,admin?"ADMIN":"CUSTOMER",admin?1:0,bookingId,actorId,actorId);
                if(allowed==null||allowed!=1) throw new org.springframework.security.access.AccessDeniedException("Không có quyền bổ sung bằng chứng cho hồ sơ này.");
                for(String path:paths)
                    jdbc.update("INSERT INTO dbo.Feedback_Case_Evidence(case_id,message_id,image_path,uploaded_by) VALUES (?,?,?,?)",caseId,messageId,path,actorId);
            });
        } catch(RuntimeException failure) { paths.forEach(this::delete); throw failure; }
    }

    @Override
    public void uploadReceipt(Long entryId, Long adminId, MultipartFile upload) {
        authorization.requireActiveAdmin(adminId);
        String path=store(upload,true);
        try {
            tx.executeWithoutResult(status -> {
                int updated=jdbc.update("UPDATE dbo.Booking_Financial_Entries SET receipt_path=? WHERE entry_id=? AND status='APPROVED' AND receipt_path IS NULL",path,entryId);
                if(updated!=1) throw new IllegalStateException("Chỉ tải chứng từ cho khoản đã được duyệt và chưa quyết toán.");
            });
        } catch(RuntimeException failure) { delete(path); throw failure; }
    }

    @Override
    public Path customerEvidence(Long evidenceId, Long customerId, boolean admin) {
        if(admin) authorization.requireActiveAdmin(customerId);
        String value=jdbc.query("""
                SELECT e.image_path FROM dbo.Feedback_Case_Evidence e JOIN dbo.Feedback_Cases c ON c.case_id=e.case_id
                JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id WHERE e.evidence_id=? AND (?=1 OR f.customer_id=?)
                """,rs -> rs.next()?rs.getString(1):null,evidenceId,admin?1:0,customerId);
        return secure(value);
    }

    @Override
    public Path receipt(Long entryId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        String value=jdbc.query("SELECT receipt_path FROM dbo.Booking_Financial_Entries WHERE entry_id=?",rs -> rs.next()?rs.getString(1):null,entryId);
        return secure(value);
    }

    @Override
    public java.util.List<Long> customerEvidenceIds(Long caseId, Long customerId) {
        return jdbc.query("""
                SELECT e.evidence_id FROM dbo.Feedback_Case_Evidence e JOIN dbo.Feedback_Cases c ON c.case_id=e.case_id
                JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id WHERE e.case_id=? AND f.customer_id=? ORDER BY e.uploaded_at,e.evidence_id
                """,(rs,row)->rs.getLong(1),caseId,customerId);
    }

    @Override
    public java.util.List<Long> adminEvidenceIds(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return jdbc.query("SELECT evidence_id FROM dbo.Feedback_Case_Evidence WHERE case_id=? ORDER BY uploaded_at,evidence_id",(rs,row)->rs.getLong(1),caseId);
    }

    private String store(MultipartFile upload, boolean allowPdf) {
        if(upload==null||upload.isEmpty()||upload.getSize()>MAX_BYTES) throw new IllegalArgumentException("Tệp phải có nội dung và không vượt quá 5 MB.");
        String name=upload.getOriginalFilename(); if(name==null) throw new IllegalArgumentException("Tên tệp không hợp lệ.");
        String lower=name.toLowerCase(Locale.ROOT), ext;
        try {
            byte[] bytes=upload.getBytes();
            if(allowPdf && lower.endsWith(".pdf") && bytes.length>4 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F') ext=".pdf";
            else if(lower.endsWith(".png") && bytes.length>8 && (bytes[0]&255)==137 && bytes[1]=='P' && bytes[2]=='N' && bytes[3]=='G' && decodeImage(bytes,"png")) ext=".png";
            else if((lower.endsWith(".jpg")||lower.endsWith(".jpeg")) && bytes.length>3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255 && decodeImage(bytes,"jpeg")) ext=lower.endsWith(".jpg")?".jpg":".jpeg";
            else throw new IllegalArgumentException("Chỉ chấp nhận hóa đơn PDF hoặc ảnh JPEG/PNG hợp lệ.");
            Files.createDirectories(root); Path target=root.resolve(UUID.randomUUID()+ext).normalize();
            if(!target.getParent().equals(root)) throw new IllegalStateException("Đường dẫn tệp không hợp lệ.");
            Path temp=Files.createTempFile(root,"case-",".upload");
            try { Files.write(temp,bytes); try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(temp,target); } }
            finally { Files.deleteIfExists(temp); }
            return target.getFileName().toString();
        } catch(IOException failure) { throw new IllegalArgumentException("Không thể đọc/lưu chứng từ.",failure); }
    }

    private boolean decodeImage(byte[] bytes,String expected) throws IOException {
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if(input==null) return false;
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) return false;
            ImageReader reader=readers.next();
            try { reader.setInput(input,true,true); return reader.getFormatName().equalsIgnoreCase(expected) && reader.read(0)!=null; }
            finally { reader.dispose(); }
        }
    }

    private Path secure(String value) {
        if(value==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        Path file=root.resolve(value).normalize();
        if(!file.getParent().equals(root)||!Files.isRegularFile(file)||Files.isSymbolicLink(file)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return file;
    }
    private void delete(String name) { try { if(name!=null) Files.deleteIfExists(root.resolve(name)); } catch(IOException ignored) { } }
}
