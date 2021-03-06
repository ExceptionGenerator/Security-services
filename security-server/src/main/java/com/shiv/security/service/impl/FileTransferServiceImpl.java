package com.shiv.security.service.impl;

import com.shiv.security.constant.ApiConstant;
import com.shiv.security.dto.CryptoSecretKeyDTO;
import com.shiv.security.dto.SentDataDTO;
import com.shiv.security.exception.GenericException;
import com.shiv.security.service.CryptoService;
import com.shiv.security.service.FileTransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileTransferServiceImpl implements FileTransferService {

    private File file;

    @Autowired
    private CryptoService cryptoService;

    @Override
    public ResponseEntity<?> sendFile(MultipartFile multipartFile,final String ipAddress) throws GenericException, IOException {
        String uuid=UUID.randomUUID().toString();
        InputStream inputStream=multipartFile.getInputStream();
        File file=new File(ApiConstant.SERVER_DOWNLOAD_DIR+File.separator+ ipAddress.replace(":",""));
        file.mkdirs();
        FileOutputStream fileOutputStream=new FileOutputStream(file.getAbsolutePath()+File.separator+uuid+multipartFile.getOriginalFilename());
        fileOutputStream.write(cryptoService.encryptFileData(inputStream.readAllBytes(),uuid));
        fileOutputStream.flush();
        fileOutputStream.close();
        inputStream.close();
        return ResponseEntity.status(HttpStatus.OK).body(new CryptoSecretKeyDTO(uuid));
    }

    @Override
    public ResponseEntity<?> receiveFile(CryptoSecretKeyDTO cryptoSecretKeyDTO) throws GenericException, IOException {
        File rootPath=new File(ApiConstant.SERVER_DOWNLOAD_DIR);
        searchFileViaSecretKey(rootPath,cryptoSecretKeyDTO);
        rootPath=file;
        if(file==null)
            throw new GenericException(HttpStatus.NOT_FOUND.value(), "Incorrect given key");
        FileInputStream fileInputStream=new FileInputStream(rootPath);
        ByteArrayResource byteArrayResource=new ByteArrayResource(cryptoService.decryptFileData(fileInputStream.readAllBytes(), cryptoSecretKeyDTO.getSecretKey()));
        fileInputStream.close();
        HttpHeaders httpHeaders=new HttpHeaders();
        httpHeaders.add("status","File is ready to download you can able to download");
        httpHeaders.add(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+rootPath.getName().replace(cryptoSecretKeyDTO.getSecretKey(), ""));
        file.delete();
        this.file=null;
        return ResponseEntity.status(HttpStatus.OK).headers(httpHeaders).contentLength(byteArrayResource.contentLength()).contentType(MediaType.APPLICATION_OCTET_STREAM).body(byteArrayResource);
    }

    @Override
    public ResponseEntity<?> getSentFileKeys(final String ipAddress) throws IOException, GenericException {
        File rootPath=new File(ApiConstant.SERVER_DOWNLOAD_DIR);
        List<SentDataDTO> sentDataDTOS=getListFilesViaIPAddress(rootPath,ipAddress);
        if(sentDataDTOS==null || sentDataDTOS.size()==0)
            throw new GenericException(HttpStatus.NOT_FOUND.value(), "No any sent files found via your device");
        return ResponseEntity.status(HttpStatus.OK).body(sentDataDTOS);
    }

    /**
     * searching file via secretKey
     * @param rootPath
     * @param cryptoSecretKeyDTO
     * @return
     */
    private void searchFileViaSecretKey(File rootPath,CryptoSecretKeyDTO cryptoSecretKeyDTO){
        File[] files=rootPath.listFiles();
        if(files!=null)
            Arrays.stream(files).forEach((file)->{
                if(file!=null)
                    if(file.isDirectory())
                        searchFileViaSecretKey(file,cryptoSecretKeyDTO);
                    else
                        if(cryptoSecretKeyDTO.getSecretKey().equals(file.getName().substring(0,36)))
                            this.file=file;
            });
    }

    /**
     * filter sent files via ip address
     * @param rootPath
     * @return
     * @throws IOException
     */
    private List<SentDataDTO> getListFilesViaIPAddress(File rootPath,String ipAddress) throws IOException {
        File[] files = rootPath.listFiles();
        List<SentDataDTO> sentDataDTOS=new LinkedList<>();
        if (files != null){
            List<File> ipDirectoryList= Arrays.stream(files).filter((file) -> file.isDirectory() && file.getName().equals(ipAddress.replace(":", ""))).collect(Collectors.toList());
            ipDirectoryList.forEach((file1 -> {
                SimpleDateFormat simpleDateFormat=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                if(file1!=null)
                    Arrays.stream(file1.listFiles()).forEach((file2 ->
                            sentDataDTOS.add(new SentDataDTO(file2.getName().substring(36),file2.getName().substring(0,36), simpleDateFormat.format(file2.lastModified())))));
            }));
        }
        return sentDataDTOS;
    }
}
