/*
 * This project is licensed as below.
 *
 * **************************************************************************
 *
 * Copyright 2020-2021 Intel Corporation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * **************************************************************************
 *
 */

package com.intel.bkp.verifier.service.certificate;

import com.intel.bkp.verifier.Utils;
import com.intel.bkp.verifier.x509.X509CertificateParser;
import com.intel.bkp.verifier.x509.X509CrlParentVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;

import static com.intel.bkp.ext.crypto.x509.X509CrlParser.toX509Crl;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CrlVerifierTestIT {

    private static final String TEST_FOLDER = "certs/diceChain/";
    private static final X509CertificateParser X509_PARSER = new X509CertificateParser();

    private static final String FIRMWARE_CRL_URL = "https://tsci.intel.com/content/IPCS/crls/IPCS_agilex_L1.crl";
    private static final String DEVICEID_CRL_URL = "https://tsci.intel.com/content/IPCS/crls/IPCS_agilex.crl";
    private static final String PRODUCTFAMILY_CRL_URL = "https://tsci.intel.com/content/DICE/crls/DICE.crl";

    private static X509Certificate aliasCert;
    private static X509Certificate firmwareCert;
    private static X509CRL firmwareCrl;
    private static X509Certificate deviceIdCert;
    private static X509CRL deviceIdCrl;
    private static X509Certificate productFamilyCert;
    private static X509CRL productFamilyCrl;
    private static X509Certificate rootCert;

    private static List<X509Certificate> list;

    @Mock
    private ICrlProvider crlProvider;

    private CrlVerifier sut;

    @BeforeEach
    void prepareSut() {
        sut = new CrlVerifier(X509_PARSER, new X509CrlParentVerifier(), crlProvider);
    }

    @BeforeAll
    static void init() throws Exception {
        aliasCert = X509_PARSER.toX509(getBytesFromFile("UDS_EFUSE_ALIAS_3AB5A0DC4DE7CB08.cer"));
        firmwareCert = X509_PARSER.toX509(getBytesFromFile("FIRMWARE_3AB5A0DC4DE7CB08.cer"));
        deviceIdCert = X509_PARSER.toX509(getBytesFromFile("deviceid_08cbe74ddca0b53a_7eukZEEF-nzSZWoHQrqQf53ru9A"
            + ".cer"));
        productFamilyCert = X509_PARSER.toX509(getBytesFromFile("IPCS_agilex.cer"));
        rootCert = X509_PARSER.toX509(getBytesFromFile("DICE_RootCA.cer"));

        list = List.of(aliasCert, firmwareCert, deviceIdCert, productFamilyCert, rootCert);

        firmwareCrl = toX509Crl(getBytesFromFile("IPCS_agilex_L1.crl"));
        deviceIdCrl = toX509Crl(getBytesFromFile("IPCS_agilex.crl"));
        productFamilyCrl = toX509Crl(getBytesFromFile("DICE.crl"));
    }

    private static byte[] getBytesFromFile(String filename) throws Exception {
        return Utils.readFromResources(TEST_FOLDER, filename);
    }

    @Test
    void verify_WithNotRequiredCrlForLeaf_ReturnsTrue() {
        // given
        when(crlProvider.getCrl(FIRMWARE_CRL_URL)).thenReturn(firmwareCrl);
        when(crlProvider.getCrl(DEVICEID_CRL_URL)).thenReturn(deviceIdCrl);
        when(crlProvider.getCrl(PRODUCTFAMILY_CRL_URL)).thenReturn(productFamilyCrl);

        // when
        boolean result = sut.certificates(list).doNotRequireCrlForLeafCertificate().verify();

        // then
        Assertions.assertTrue(result);
    }

    @Test
    void verify_WithRequiredCrlForLeaf_ReturnsFalse() {
        // when
        boolean result = sut.certificates(list).verify();

        // then
        Assertions.assertFalse(result);
    }
}
