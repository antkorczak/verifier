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

import com.intel.bkp.verifier.exceptions.CrlSignatureException;
import com.intel.bkp.verifier.exceptions.SigmaException;
import com.intel.bkp.verifier.exceptions.X509ParsingException;
import com.intel.bkp.verifier.x509.X509CertificateParser;
import com.intel.bkp.verifier.x509.X509CrlParentVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrlVerifierTest {

    private static final BigInteger REVOKED_SERIAL_NUMBER = BigInteger.ONE;
    private static final BigInteger NOT_REVOKED_SERIAL_NUMBER = BigInteger.TWO;
    private static final String LEAF_CRL_PATH = "Leaf CRL URL";
    private static final String PARENT_CRL_PATH = "Parent CRL URL";

    @Mock
    private X509CRL leafCRL;

    @Mock
    private X509CRL parentCRL;

    @Mock
    private PublicKey parentPublicKey;

    @Mock
    private PublicKey rootPublicKey;

    @Mock
    private X509CrlParentVerifier x509CrlParentVerifier;

    @Mock
    private X509CertificateParser certificateParser;

    @Mock
    private ICrlProvider crlProvider;

    @Mock
    private List<X509Certificate> certificates;

    @Mock
    private X509Certificate leafCertificate;

    @Mock
    private X509Certificate parentCertificate;

    @Mock
    private X509Certificate rootCertificate;

    @Mock
    private ListIterator<X509Certificate> certificateChainIterator;

    @Mock
    private ListIterator<X509Certificate> leafCertIssuerCertsIterator;

    @Mock
    private ListIterator<X509Certificate> parentCertIssuerCertsIterator;

    @InjectMocks
    private CrlVerifier sut;

    @BeforeEach
    void setUpClass() {
        sut.certificates(certificates);
    }

    @Test
    void verify_NotRevokedDevice_Success() {
        // given
        mockChainWith2Certs();
        mockLeafCrlOnDp();
        mockLeafCrlSignedByDirectParent();
        mockLeafCertIsNotRevoked();

        // when-then
        Assertions.assertTrue(() -> sut.verify());

        // then
        verify(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
    }

    @Test
    void verify_WithRevokedDevice_ReturnFalse() {
        // given
        mockChainWith2Certs();
        mockLeafCrlOnDp();
        mockLeafCrlSignedByDirectParent();
        mockLeafCertIsRevoked();

        // when-then
        Assertions.assertFalse(() -> sut.verify());

        // then
        verify(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
    }

    @Test
    void verify_WithRevokedIntermediateCert_Throws() {
        // given
        mockChainWith3Certs();
        mockLeafCrlOnDp();
        mockLeafCrlSignedByDirectParent();
        mockParentCrlOnDp();
        mockParentCrlSignedByRoot();
        mockParentCertIsRevoked();

        // when-then
        Assertions.assertThrows(SigmaException.class, () -> sut.verify());
    }

    @Test
    void verify_CrlSignedNotByDirectCertIssuer_Success() {
        // given
        mockChainWith3Certs();
        mockLeafCrlOnDp();
        mockLeafCrlSignedByRoot();
        mockParentCrlOnDp();
        mockParentCrlSignedByRoot();

        // when-then
        Assertions.assertTrue(() -> sut.verify());

        // then
        verify(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
        verify(x509CrlParentVerifier).verify(leafCRL, rootPublicKey);
        verify(x509CrlParentVerifier).verify(parentCRL, rootPublicKey);
    }

    @Test
    void verify_CrlNotSignedByAnyCertInChain_Throws() {
        // given
        mockChainWith3Certs();
        mockLeafCrlOnDp();
        mockLeafCrlNotSignedByAnyCertInChain();

        // when-then
        CrlSignatureException ex = Assertions.assertThrows(CrlSignatureException.class, () -> sut.verify());

        // then
        Assertions.assertEquals("Failed to verify signature of CRL", ex.getMessage());
        verify(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
        verify(x509CrlParentVerifier).verify(leafCRL, rootPublicKey);
    }

    @Test
    void verify_WithCertWithoutCrlExtension_CrlNotRequired_ReturnTrue() {
        // given
        mockChainWith2Certs();
        mockLeafCertDoesNotContainUrlToCrl();

        // when-then
        Assertions.assertTrue(() -> sut.doNotRequireCrlForLeafCertificate().verify());

        // then
        verifyNoInteractions(x509CrlParentVerifier);
    }

    @Test
    void verify_WithCertWithoutCrlExtension_CrlRequired_ReturnFalse() {
        // given
        mockChainWith2Certs();
        mockLeafCertDoesNotContainUrlToCrl();

        // when-then
        Assertions.assertFalse(() -> sut.verify());

        // then
        verifyNoInteractions(x509CrlParentVerifier);
    }

    private void mockChainWith3Certs() {
        when(certificates.listIterator()).thenReturn(certificateChainIterator);
        when(certificateChainIterator.hasNext()).thenReturn(true, true, false);
        when(certificateChainIterator.next()).thenReturn(leafCertificate, parentCertificate, rootCertificate);
    }

    private void mockChainWith2Certs() {
        when(certificates.listIterator()).thenReturn(certificateChainIterator);
        when(certificateChainIterator.hasNext()).thenReturn(true, false);
        when(certificateChainIterator.next()).thenReturn(leafCertificate, parentCertificate);
    }

    private void mockLeafCrlOnDp() {
        when(certificateParser.getPathToCrlDistributionPoint(leafCertificate)).thenReturn(Optional.of(LEAF_CRL_PATH));
        when(crlProvider.getCrl(LEAF_CRL_PATH)).thenReturn(leafCRL);
    }

    private void mockLeafCertDoesNotContainUrlToCrl() {
        when(certificateParser.getPathToCrlDistributionPoint(leafCertificate)).thenReturn(Optional.empty());
    }

    private void mockParentCrlOnDp() {
        when(certificateParser.getPathToCrlDistributionPoint(parentCertificate)).thenReturn(Optional.of(PARENT_CRL_PATH));
        when(crlProvider.getCrl(PARENT_CRL_PATH)).thenReturn(parentCRL);
    }

    private void mockLeafCrlSignedByDirectParent() {
        when(certificateChainIterator.nextIndex()).thenReturn(1);
        when(certificates.listIterator(1)).thenReturn(leafCertIssuerCertsIterator);
        when(leafCertIssuerCertsIterator.hasNext()).thenReturn(true);
        when(leafCertIssuerCertsIterator.next()).thenReturn(parentCertificate);
        when(parentCertificate.getPublicKey()).thenReturn(parentPublicKey);
        doNothing().when(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
    }

    private void mockLeafCrlSignedByRoot() {
        when(certificateChainIterator.nextIndex()).thenReturn(1);
        when(certificates.listIterator(1)).thenReturn(leafCertIssuerCertsIterator);
        when(leafCertIssuerCertsIterator.hasNext()).thenReturn(true, true);
        when(leafCertIssuerCertsIterator.next()).thenReturn(parentCertificate, rootCertificate);
        when(parentCertificate.getPublicKey()).thenReturn(parentPublicKey);
        doThrow(new X509ParsingException("")).when(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
        doNothing().when(x509CrlParentVerifier).verify(leafCRL, rootPublicKey);
    }

    private void mockLeafCrlNotSignedByAnyCertInChain() {
        when(certificateChainIterator.nextIndex()).thenReturn(1);
        when(certificates.listIterator(1)).thenReturn(leafCertIssuerCertsIterator);
        when(leafCertIssuerCertsIterator.hasNext()).thenReturn(true, true, false);
        when(leafCertIssuerCertsIterator.next()).thenReturn(parentCertificate, rootCertificate);
        when(parentCertificate.getPublicKey()).thenReturn(parentPublicKey);
        when(rootCertificate.getPublicKey()).thenReturn(rootPublicKey);
        doThrow(new X509ParsingException("")).when(x509CrlParentVerifier).verify(leafCRL, parentPublicKey);
        doThrow(new X509ParsingException("")).when(x509CrlParentVerifier).verify(leafCRL, rootPublicKey);
    }

    private void mockParentCrlSignedByRoot() {
        when(certificateChainIterator.nextIndex()).thenReturn(1, 2);
        when(certificates.listIterator(2)).thenReturn(parentCertIssuerCertsIterator);
        when(parentCertIssuerCertsIterator.hasNext()).thenReturn(true, false);
        when(parentCertIssuerCertsIterator.next()).thenReturn(rootCertificate);
        when(rootCertificate.getPublicKey()).thenReturn(rootPublicKey);
        doNothing().when(x509CrlParentVerifier).verify(parentCRL, rootPublicKey);
    }

    private void mockLeafCertIsNotRevoked() {
        mockSerialNumber(leafCertificate, NOT_REVOKED_SERIAL_NUMBER);
        mockCrlWithRevokedEntry(leafCRL, REVOKED_SERIAL_NUMBER);
    }

    private void mockLeafCertIsRevoked() {
        mockSerialNumber(leafCertificate, REVOKED_SERIAL_NUMBER);
        mockCrlWithRevokedEntry(leafCRL, REVOKED_SERIAL_NUMBER);
    }

    private void mockParentCertIsRevoked() {
        mockSerialNumber(parentCertificate, REVOKED_SERIAL_NUMBER);
        mockCrlWithRevokedEntry(parentCRL, REVOKED_SERIAL_NUMBER);
        when(certificateChainIterator.previousIndex()).thenReturn(1);
    }

    private void mockSerialNumber(X509Certificate certificate, BigInteger serialNumber) {
        when(certificate.getSerialNumber()).thenReturn(serialNumber);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockCrlWithRevokedEntry(X509CRL crl, BigInteger revokedSerialNumber) {
        final X509CRLEntry crlEntry = mock(X509CRLEntry.class);
        when(crl.getRevokedCertificates()).thenReturn((Set) Set.of(crlEntry));
        when(crlEntry.getSerialNumber()).thenReturn(revokedSerialNumber);
    }
}
