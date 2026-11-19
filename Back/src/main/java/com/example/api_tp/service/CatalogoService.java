package com.example.api_tp.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.api_tp.dto.CatalogoDenominacionDTO;
import com.example.api_tp.dto.ListaPrecioArticuloDTO;
import com.example.api_tp.dto.PuntoVentaDTO;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.ListaPrecio;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.Rubro;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.repository.ClienteRepository;
import com.example.api_tp.repository.CondicionIvaRepository;
import com.example.api_tp.repository.ListaPrecioArticuloRepository;
import com.example.api_tp.repository.ListaPrecioRepository;
import com.example.api_tp.repository.PuntoVentaRepository;
import com.example.api_tp.repository.RubroRepository;
import com.example.api_tp.repository.TipoMonedaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final PuntoVentaRepository puntoVentaRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioRepository listaPrecioRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final RubroRepository rubroRepository;

    @Transactional(readOnly = true)
    public List<PuntoVentaDTO> obtenerPuntosVenta() {
        return puntoVentaRepository.findAll(Sort.by(PuntoVenta::getId)).stream()
                .map(punto -> new PuntoVentaDTO(punto.getId(), punto.getDescripcion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDenominacionDTO> obtenerClientes() {
        return clienteRepository.findAll(Sort.by(Cliente::getId)).stream()
                .map(cliente -> new CatalogoDenominacionDTO(cliente.getId(), cliente.getDenominacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDenominacionDTO> obtenerCondicionesIva() {
        return condicionIvaRepository.findAll(Sort.by(CondicionIva::getId)).stream()
                .map(condicion -> new CatalogoDenominacionDTO(
                        condicion.getId(), condicion.getDenominacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDenominacionDTO> obtenerTiposMoneda() {
        return tipoMonedaRepository.findAll(Sort.by(TipoMoneda::getId)).stream()
                .map(moneda -> new CatalogoDenominacionDTO(moneda.getId(), moneda.getDenominacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDenominacionDTO> obtenerListasPrecio() {
        return listaPrecioRepository.findAll(Sort.by(ListaPrecio::getId)).stream()
                .map(lista -> new CatalogoDenominacionDTO(lista.getId(), lista.getDenominacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDenominacionDTO> obtenerRubros() {
        return rubroRepository.findAll(Sort.by(Rubro::getDenominacion)).stream()
                .map(rubro -> new CatalogoDenominacionDTO(rubro.getId(), rubro.getDenominacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ListaPrecioArticuloDTO> obtenerArticulosDeLista(Long listaPrecioId) {
        if (listaPrecioId == null || listaPrecioId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "listaPrecioId debe ser un entero positivo");
        }
        if (!listaPrecioRepository.existsById(listaPrecioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Lista de precio con ID " + listaPrecioId + " no existe");
        }
        List<ListaPrecioArticulo> articulos = listaPrecioArticuloRepository
                .buscarPorListaPrecioId(listaPrecioId);
        return articulos.stream()
                .map(registro -> new ListaPrecioArticuloDTO(
                        registro.getId(),
                        registro.getArticulo().getId(),
                        registro.getArticulo().getDenominacion(),
                        registro.getArticulo().getRubro().getDenominacion(),
                        registro.getPrecioVenta()))
                .toList();
    }
}