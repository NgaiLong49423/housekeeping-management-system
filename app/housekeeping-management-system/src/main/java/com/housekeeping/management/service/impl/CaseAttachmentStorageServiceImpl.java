package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.repository.FeedbackCaseEvidenceRepository;
import com.housekeeping.management.repository.StaffIncidentActionEvidenceRepository;
import com.housekeeping.management.repository.BookingFinancialRepository;
import com.housekeeping.management.entity.FeedbackCaseEvidence;
import com.housekeeping.management.entity.StaffIncidentActionEvidence;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CaseAttachmentStorageServiceImpl implements CaseAttachmentStorageService {
    private static final long MAX_BYTES=5L*1024*1024;
    private static final long MAX_VIDEO_BYTES=50L*1024*1024;
    private final FeedbackCaseService cases;
    private final FeedbackCaseEvidenceRepository evidence;
    private final StaffIncidentActionEvidenceRepository staffActionEvidence;
    private final BookingFinancialRepository financialEntries;
    private final BookingReservationService bookings;
    private final BookingFinancialService financials;
    private final TransactionTemplate tx;
    private final Path root;
    private final AccountAuthorizationService authorization;
    public CaseAttachmentStorageServiceImpl(FeedbackCaseService cases, FeedbackCaseEvidenceRepository evidence,
            StaffIncidentActionEvidenceRepository staffActionEvidence,
            BookingFinancialRepository financialEntries, PlatformTransactionManager manager,
            @Value("${app.evidence.storage-root:./var/evidence}") String root, AccountAuthorizationService authorization,
            BookingReservationService bookings, BookingFinancialService financials) {
        this.cases=cases; this.evidence=evidence; this.staffActionEvidence=staffActionEvidence; this.financialEntries=financialEntries;
        this.tx=new TransactionTemplate(manager); this.root=Path.of(root).toAbsolutePath().normalize(); this.authorization=authorization;
        this.bookings=bookings;
        this.financials=financials;
    }

    @Override
    public void uploadCustomerEvidence(Long caseId, Long bookingId, Long customerId, MultipartFile upload) {
        uploadCustomerEvidence(caseId, bookingId, customerId, java.util.List.of(upload));
    }

    @Override
    public void uploadCustomerEvidence(Long caseId, Long bookingId, Long customerId, java.util.List<MultipartFile> uploads) {
        Long initialMessage=cases.initialCustomerMessageId(caseId);
        if(initialMessage==null) throw new IllegalStateException("Chưa có tin nhắn đầu tiên của hồ sơ sự cố.");
        uploadCustomerMessageEvidence(caseId,bookingId,customerId,initialMessage,uploads);
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
            throw new IllegalArgumentException("Mỗi tin nhắn cần có từ 1 đến 5 tệp hợp lệ.");
        var existing=evidence.findAllByMessageIdOrderByUploadedAtAscEvidenceIdAsc(messageId);
        long incomingVideos=uploads.stream().filter(this::looksLikeVideoName).count();
        if(existing.size()+uploads.size()>5 || existing.stream().filter(item->"VIDEO".equals(item.getMediaType())).count()+incomingVideos>1)
            throw new IllegalArgumentException("Mỗi tin nhắn tối đa 5 tệp và chỉ được có một video.");
        java.util.List<StoredFile> stored=new java.util.ArrayList<>();
        try {
            for(MultipartFile upload:uploads) stored.add(storeMessageFile(upload));
            tx.executeWithoutResult(status -> {
                if(!cases.authorizesMessageEvidence(caseId,messageId,actorId,admin,bookingId))
                    throw new org.springframework.security.access.AccessDeniedException("Không có quyền bổ sung bằng chứng cho hồ sơ này.");
                for(StoredFile file:stored) {
                    FeedbackCaseEvidence item = new FeedbackCaseEvidence();
                    item.setCaseId(caseId); item.setMessageId(messageId); item.setImagePath(file.path()); item.setMediaType(file.type());
                    item.setContentType(file.contentType()); item.setOriginalName(file.originalName()); item.setExternalUrl(null); item.setUploadedBy(actorId);
                    item.setUploadedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
                    evidence.save(item);
                }
            });
        } catch(RuntimeException failure) { stored.forEach(file->delete(file.path())); throw failure; }
    }

    @Override
    public void addCustomerDriveEvidence(Long caseId, Long bookingId, Long customerId, Long messageId, String url) {
        saveDriveLink(caseId,messageId,customerId,false,bookingId,url);
    }

    @Override
    public void addAdminDriveEvidence(Long caseId, Long adminId, Long messageId, String url) {
        authorization.requireActiveAdmin(adminId);
        saveDriveLink(caseId,messageId,adminId,true,null,url);
    }

    private void saveDriveLink(Long caseId, Long messageId, Long actorId, boolean admin, Long bookingId, String url) {
        String clean=url==null?"":url.trim();
        try {
            java.net.URI uri=java.net.URI.create(clean);
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || !(host.equals("drive.google.com") || host.equals("docs.google.com")) || clean.length()>1000)
                throw new IllegalArgumentException("Chỉ nhận liên kết HTTPS từ Google Drive.");
        } catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("Liên kết Google Drive không hợp lệ."); }
        tx.executeWithoutResult(status -> {
            if(!cases.authorizesMessageEvidence(caseId,messageId,actorId,admin,bookingId))
                throw new org.springframework.security.access.AccessDeniedException("Không có quyền bổ sung bằng chứng cho hồ sơ này.");
            var existing=evidence.findAllByMessageIdOrderByUploadedAtAscEvidenceIdAsc(messageId);
            if(existing.size()>=5) throw new IllegalArgumentException("Mỗi tin nhắn tối đa 5 tệp bằng chứng.");
            FeedbackCaseEvidence item=new FeedbackCaseEvidence(); item.setCaseId(caseId); item.setMessageId(messageId);
            item.setImagePath(null); item.setMediaType("DRIVE_LINK"); item.setContentType("text/uri-list");
            item.setOriginalName("Liên kết Google Drive"); item.setExternalUrl(clean); item.setUploadedBy(actorId);
            item.setUploadedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0)); evidence.save(item);
        });
    }

    @Override
    public void uploadReceipt(Long entryId, Long adminId, MultipartFile upload) {
        authorization.requireActiveAdmin(adminId);
        financials.validateReceiptUpload(entryId,adminId);
        String path=store(upload,true);
        try {
            tx.executeWithoutResult(status -> {
                int updated=financialEntries.attachReceipt(entryId,path);
                if(updated!=1) throw new IllegalStateException("Chỉ tải chứng từ cho khoản đã được duyệt và chưa quyết toán.");
            });
        } catch(RuntimeException failure) { delete(path); throw failure; }
    }

    @Override
    public Path customerEvidence(Long evidenceId, Long customerId, boolean admin) {
        if(admin) authorization.requireActiveAdmin(customerId);
        var item=evidence.findById(evidenceId).orElse(null);
        String value=item==null?null:item.getImagePath();
        if(!admin && !cases.customerOwnsEvidence(evidenceId,customerId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return secure(value);
    }

    @Override
    public Path receipt(Long entryId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        String value=financialEntries.findById(entryId).map(com.housekeeping.management.entity.BookingFinancialEntry::getReceiptPath).orElse(null);
        return secure(value);
    }

    @Override
    public Path customerReceipt(Long entryId, Long customerId) {
        var entry=financialEntries.findById(entryId).orElse(null);
        if(entry==null || entry.getReceiptPath()==null || !java.util.List.of("APPROVED", "PAID").contains(entry.getStatus())
                || !bookings.bookingCustomerId(entry.getBookingId()).filter(customerId::equals).isPresent())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return secure(entry.getReceiptPath());
    }

    @Override
    public java.util.List<Long> customerEvidenceIds(Long caseId, Long customerId) {
        if(!cases.customerOwnsCase(caseId,customerId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(caseId).stream()
                .map(FeedbackCaseEvidence::getEvidenceId).toList();
    }

    @Override
    public java.util.List<Long> adminEvidenceIds(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(caseId).stream()
                .map(FeedbackCaseEvidence::getEvidenceId).toList();
    }

    @Override
    public void uploadStaffActionEvidence(Long actionId, Long staffId, Long staffUserId, String stage, java.util.List<MultipartFile> uploads) {
        if(uploads==null || uploads.isEmpty() || uploads.size()>5 || uploads.stream().anyMatch(file->file==null || file.isEmpty()))
            throw new IllegalArgumentException("Đính kèm từ 1 đến 5 ảnh/PDF/video hợp lệ.");
        java.util.List<StoredFile> stored=new java.util.ArrayList<>();
        try {
            for(MultipartFile upload:uploads) stored.add(storeMessageFile(upload));
            tx.executeWithoutResult(status -> {
                if(!cases.staffOwnsAction(actionId,staffId)) throw new org.springframework.security.access.AccessDeniedException("Không có quyền tải bằng chứng lên hồ sơ này.");
                java.util.List<StaffIncidentActionEvidence> rows=new java.util.ArrayList<>();
                for(StoredFile file:stored) {
                    StaffIncidentActionEvidence row=new StaffIncidentActionEvidence(); row.setActionId(actionId); row.setUploadedBy(staffUserId);
                    row.setEvidenceStage(stage); row.setMediaType(file.type()); row.setContentType(file.contentType());
                    row.setOriginalName(file.originalName()); row.setStoragePath(file.path());
                    row.setUploadedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0)); rows.add(row);
                }
                cases.addStaffActionEvidence(actionId,staffId,staffUserId,stage,rows);
            });
        } catch(RuntimeException failure) { stored.forEach(file->delete(file.path())); throw failure; }
    }

    @Override
    public Path staffActionEvidence(Long evidenceId, Long actorId, boolean admin) {
        return secure(cases.staffActionEvidencePath(evidenceId,actorId,admin).getFileName().toString());
    }

    @Override
    public String storeStaffRecoveryReceipt(Long actionId, Long adminId, MultipartFile upload) {
        authorization.requireActiveAdmin(adminId);
        if(authorization.isManager(adminId)) throw new org.springframework.security.access.AccessDeniedException("Admin lưu chứng từ khoản Staff thực trả.");
        return store(upload,true);
    }

    @Override
    public void saveStaffRecoveryInstallment(Long actionId, Long adminId,
            com.housekeeping.management.dto.request.PortalForms.StaffRecoveryInstallmentForm form, MultipartFile receipt) {
        String path=storeStaffRecoveryReceipt(actionId,adminId,receipt);
        try {
            tx.executeWithoutResult(status -> cases.recordStaffRecoveryInstallment(actionId,adminId,form,path));
        } catch(RuntimeException failure) {
            delete(path);
            throw failure;
        }
    }

    @Override
    public Path staffRecoveryReceipt(Long installmentId, Long adminId) {
        return secure(cases.staffRecoveryReceiptPath(installmentId,adminId).getFileName().toString());
    }

    private String store(MultipartFile upload, boolean allowPdf) {
        StoredFile file=storeFile(upload,allowPdf,false);
        return file.path();
    }

    private StoredFile storeMessageFile(MultipartFile upload) { return storeFile(upload,true,true); }

    private StoredFile storeFile(MultipartFile upload, boolean allowPdf, boolean allowVideo) {
        if(upload==null||upload.isEmpty()) throw new IllegalArgumentException("Tệp phải có nội dung.");
        String name=upload.getOriginalFilename(); if(name==null) throw new IllegalArgumentException("Tên tệp không hợp lệ.");
        String lower=name.toLowerCase(Locale.ROOT), ext, type, contentType;
        boolean video=lower.endsWith(".mp4")||lower.endsWith(".webm");
        long max=video?MAX_VIDEO_BYTES:MAX_BYTES;
        if(upload.getSize()>max) throw new IllegalArgumentException(video?"Video không được vượt quá 50 MB.":"Ảnh/PDF không được vượt quá 5 MB.");
        try {
            byte[] bytes=upload.getBytes();
            if(allowVideo && lower.endsWith(".mp4") && bytes.length>12 && bytes[4]=='f' && bytes[5]=='t' && bytes[6]=='y' && bytes[7]=='p') { ext=".mp4";type="VIDEO";contentType="video/mp4"; }
            else if(allowVideo && lower.endsWith(".webm") && bytes.length>4 && (bytes[0]&255)==0x1A && (bytes[1]&255)==0x45 && (bytes[2]&255)==0xDF && (bytes[3]&255)==0xA3) { ext=".webm";type="VIDEO";contentType="video/webm"; }
            else if(allowPdf && lower.endsWith(".pdf") && bytes.length>4 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F') { ext=".pdf";type="PDF";contentType="application/pdf"; }
            else if(lower.endsWith(".png") && bytes.length>8 && (bytes[0]&255)==137 && bytes[1]=='P' && bytes[2]=='N' && bytes[3]=='G' && decodeImage(bytes,"png")) { ext=".png";type="IMAGE";contentType="image/png"; }
            else if((lower.endsWith(".jpg")||lower.endsWith(".jpeg")) && bytes.length>3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255 && decodeImage(bytes,"jpeg")) { ext=lower.endsWith(".jpg")?".jpg":".jpeg";type="IMAGE";contentType="image/jpeg"; }
            else throw new IllegalArgumentException("Chỉ nhận ảnh JPEG/PNG, PDF, MP4 hoặc WebM hợp lệ.");
            Files.createDirectories(root); Path target=root.resolve(UUID.randomUUID()+ext).normalize();
            if(!target.getParent().equals(root)) throw new IllegalStateException("Đường dẫn tệp không hợp lệ.");
            Path temp=Files.createTempFile(root,"case-",".upload");
            try { Files.write(temp,bytes); try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(temp,target); } }
            finally { Files.deleteIfExists(temp); }
            return new StoredFile(target.getFileName().toString(),type,contentType,name);
        } catch(IOException failure) { throw new IllegalArgumentException("Không thể đọc/lưu chứng từ.",failure); }
    }

    private boolean looksLikeVideoName(MultipartFile file) {
        String name=file.getOriginalFilename(); return name!=null && (name.toLowerCase(Locale.ROOT).endsWith(".mp4")||name.toLowerCase(Locale.ROOT).endsWith(".webm"));
    }
    private record StoredFile(String path, String type, String contentType, String originalName) { }

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
